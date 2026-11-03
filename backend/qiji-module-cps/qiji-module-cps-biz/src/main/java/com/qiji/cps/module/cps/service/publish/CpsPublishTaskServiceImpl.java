package com.qiji.cps.module.cps.service.publish;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiji.cps.framework.common.exception.ServiceException;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.cps.controller.admin.publish.vo.*;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishLogDO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishTaskDO;
import com.qiji.cps.module.cps.dal.dataobject.rebate.CpsRebateAccountDO;
import com.qiji.cps.module.cps.dal.mysql.publish.CpsPublishLogMapper;
import com.qiji.cps.module.cps.dal.mysql.publish.CpsPublishTaskMapper;
import com.qiji.cps.module.cps.dal.mysql.rebate.CpsRebateAccountMapper;
import com.qiji.cps.module.cps.enums.CpsErrorCodeConstants;
import com.qiji.cps.module.cps.service.publish.channel.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

@Service
@Validated
public class CpsPublishTaskServiceImpl implements CpsPublishTaskService {
    @Resource
    private CpsPublishTaskMapper publishTaskMapper;
    @Resource
    private CpsPublishLogMapper publishLogMapper;
    @Resource
    private CpsRebateAccountMapper rebateAccountMapper;
    @Resource
    private RedissonClient redissonClient;
    @Autowired
    private List<CpsPublishChannel> publishChannels;
    private final Map<String, CpsPublishChannel> channelMap = new HashMap<>();
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    @PostConstruct
    public void init() {
        for (CpsPublishChannel channel : publishChannels) {
            channelMap.put(channel.getChannelCode(), channel);
        }
    }

    @Override
    public Long createTask(CpsPublishTaskSaveReqVO reqVO) {
        CpsPublishTaskDO task = BeanUtils.toBean(reqVO, CpsPublishTaskDO.class);
        validateTask(task);
        task.setId(null);
        task.setTaskCode(resolveTaskCode(reqVO.getTaskCode()));
        task.setStatus(CpsPublishConstants.TaskStatus.DRAFT);
        task.setNextDispatchTime(null);
        publishTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    public void updateTask(CpsPublishTaskSaveReqVO reqVO) {
        withLock(reqVO.getId(), () -> {
            CpsPublishTaskDO existing = getExistingTask(reqVO.getId());
            CpsPublishTaskDO update = BeanUtils.toBean(reqVO, CpsPublishTaskDO.class);
            validateTask(update);
            update.setTaskCode(null);
            update.setStatus(null);
            publishTaskMapper.updateById(update);
            LocalDateTime next = CpsPublishConstants.TaskStatus.ONLINE.equals(existing.getStatus())
                    ? calculateNextDispatchTime(update, LocalDateTime.now(clock)) : null;
            publishTaskMapper.update(null, new LambdaUpdateWrapper<CpsPublishTaskDO>()
                    .eq(CpsPublishTaskDO::getId, existing.getId())
                    .set(CpsPublishTaskDO::getChannelCode, update.getChannelCode())
                    .set(CpsPublishTaskDO::getTargetType, update.getTargetType())
                    .set(CpsPublishTaskDO::getTargetIds, update.getTargetIds())
                    .set(CpsPublishTaskDO::getContentTemplate, update.getContentTemplate())
                    .set(CpsPublishTaskDO::getLinkTemplate, update.getLinkTemplate())
                    .set(CpsPublishTaskDO::getTemplateCode, update.getTemplateCode())
                    .set(CpsPublishTaskDO::getFrequencyLimitPerDay, update.getFrequencyLimitPerDay())
                    .set(CpsPublishTaskDO::getRemark, update.getRemark())
                    .set(CpsPublishTaskDO::getCronExpression, update.getCronExpression())
                    .set(CpsPublishTaskDO::getStartTime, update.getStartTime())
                    .set(CpsPublishTaskDO::getEndTime, update.getEndTime())
                    .set(CpsPublishTaskDO::getQuietStartTime, update.getQuietStartTime())
                    .set(CpsPublishTaskDO::getQuietEndTime, update.getQuietEndTime())
                    .set(CpsPublishTaskDO::getNextDispatchTime, next));
            return null;
        });
    }

    @Override
    public void deleteTask(Long id) {
        withLock(id, () -> publishTaskMapper.deleteById(id));
    }

    @Override
    public void publishTask(Long id) {
        withLock(id, () -> {
            CpsPublishTaskDO task = getExistingTask(id);
            validateTask(task);
            publishTaskMapper.update(null, new LambdaUpdateWrapper<CpsPublishTaskDO>()
                    .eq(CpsPublishTaskDO::getId, id)
                    .set(CpsPublishTaskDO::getStatus, CpsPublishConstants.TaskStatus.ONLINE)
                    .set(CpsPublishTaskDO::getNextDispatchTime, calculateNextDispatchTime(task, LocalDateTime.now(clock))));
            return null;
        });
    }

    @Override
    public void offlineTask(Long id) {
        withLock(id, () -> {
            getExistingTask(id);
            return publishTaskMapper.update(null, new LambdaUpdateWrapper<CpsPublishTaskDO>()
                    .eq(CpsPublishTaskDO::getId, id)
                    .set(CpsPublishTaskDO::getStatus, CpsPublishConstants.TaskStatus.OFFLINE)
                    .set(CpsPublishTaskDO::getNextDispatchTime, null));
        });
    }

    @Override
    public CpsPublishTaskDO getTask(Long id) { return publishTaskMapper.selectById(id); }
    @Override
    public PageResult<CpsPublishTaskDO> getTaskPage(CpsPublishTaskPageReqVO reqVO) { return publishTaskMapper.selectPage(reqVO); }
    @Override
    public PageResult<CpsPublishLogDO> getLogPage(CpsPublishLogPageReqVO reqVO) { return publishLogMapper.selectPage(reqVO); }

    @Override
    public CpsPublishDispatchRespVO dispatchNow(Long id) {
        return withLock(id, () -> dispatchTask(getExistingTask(id), LocalDateTime.now(clock), false));
    }

    @Override
    public CpsPublishDispatchRespVO dispatchDueTasks() {
        CpsPublishDispatchRespVO total = response(null, 0, 0, 0);
        for (CpsPublishTaskDO candidate : publishTaskMapper.selectDispatchableList(LocalDateTime.now(clock), 100)) {
            CpsPublishDispatchRespVO result;
            try {
                result = withLock(candidate.getId(),
                        () -> dispatchTask(getExistingTask(candidate.getId()), LocalDateTime.now(clock), true));
            } catch (Exception ex) {
                result = response(candidate, 0, 0, 1);
            }
            total.setSuccessCount(total.getSuccessCount() + result.getSuccessCount());
            total.setFailedCount(total.getFailedCount() + result.getFailedCount());
            total.setSkippedCount(total.getSkippedCount() + result.getSkippedCount());
        }
        return total;
    }

    private long tenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null || tenantId <= 0 ? 0L : tenantId;
    }

    private <T> T withLock(Long id, Supplier<T> action) {
        RLock lock = redissonClient.getLock("cps:publish:" + tenantId() + ":" + id);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private CpsPublishDispatchRespVO dispatchTask(CpsPublishTaskDO task, LocalDateTime now, boolean scheduled) {
        if (!CpsPublishConstants.TaskStatus.ONLINE.equals(task.getStatus())
                || task.getStartTime() != null && now.isBefore(task.getStartTime())
                || task.getEndTime() != null && now.isAfter(task.getEndTime())
                || scheduled && (task.getNextDispatchTime() == null || task.getNextDispatchTime().isAfter(now)
                || !StringUtils.hasText(task.getCronExpression()))) {
            return response(task, 0, 0, 1);
        }
        try {
            validateTask(task);
        } catch (ServiceException ex) {
            return response(task, 0, 0, 1);
        }
        int success = 0;
        int failed = 0;
        int skipped = 0;
        if (scheduled && inQuietHours(task, now)) {
            skipped++;
        } else {
            for (Long memberId : resolveTargetMemberIds(task)) {
                int outcome = dispatchToMember(task, memberId, now);
                if (outcome == 1) success++;
                else if (outcome == -1) failed++;
                else skipped++;
            }
        }
        publishTaskMapper.updateDispatchState(task.getId(), now, calculateNextDispatchTime(task, now));
        return response(task, success, failed, skipped);
    }

    private CpsPublishDispatchRespVO response(CpsPublishTaskDO task, int success, int failed, int skipped) {
        CpsPublishDispatchRespVO result = new CpsPublishDispatchRespVO();
        if (task != null) {
            result.setTaskId(task.getId());
            result.setTaskCode(task.getTaskCode());
        }
        result.setSuccessCount(success);
        result.setFailedCount(failed);
        result.setSkippedCount(skipped);
        result.setMessage(success + failed == 0 && skipped > 0
                ? "本次未执行：未上线、超出时间窗口、免打扰或频控跳过"
                : "已提交渠道受理或生成文案预览，不代表已送达");
        return result;
    }

    private int dispatchToMember(CpsPublishTaskDO task, Long memberId, LocalDateTime now) {
        Integer limit = task.getFrequencyLimitPerDay();
        if (limit != null && limit > 0) {
            Long count = publishLogMapper.countByTaskAndMemberSince(task.getId(), memberId, now.toLocalDate().atStartOfDay());
            if (count != null && count >= limit) return 0;
        }
        CpsPublishLogDO claim = CpsPublishLogDO.builder().taskId(task.getId()).taskCode(task.getTaskCode())
                .channelCode(task.getChannelCode()).memberId(memberId)
                .idempotentKey(tenantId() + ":" + task.getId() + ":" + memberId + ":" + now.truncatedTo(ChronoUnit.MINUTES))
                .status(CpsPublishConstants.LogStatus.PROCESSING).sentAt(now).build();
        try {
            publishLogMapper.insert(claim);
        } catch (DuplicateKeyException ex) {
            return 0;
        }
        String link = task.getLinkTemplate();
        String content = task.getContentTemplate().replace("{member_id}", memberId.toString())
                .replace("{task_name}", task.getTaskName()).replace("{url}", link == null ? "" : link);
        try {
            CpsPublishSendResult result = channelMap.get(task.getChannelCode()).send(new CpsPublishSendContext(task, memberId, content, link));
            if (result == null || !result.success()) {
                updateLogStatus(claim.getId(), "FAILED", result == null ? "渠道未受理" : result.errorMsg());
                return -1;
            }
            publishLogMapper.update(null, new LambdaUpdateWrapper<CpsPublishLogDO>()
                    .eq(CpsPublishLogDO::getId, claim.getId())
                    .set(CpsPublishLogDO::getStatus,
                            CpsPublishConstants.ChannelCode.CLIPBOARD.equals(task.getChannelCode())
                                    ? CpsPublishConstants.LogStatus.PREVIEW : CpsPublishConstants.LogStatus.ACCEPTED)
                    .set(CpsPublishLogDO::getContentSnapshot, content).set(CpsPublishLogDO::getLinkUrl, link)
                    .set(CpsPublishLogDO::getErrorMsg, null));
            return 1;
        } catch (Exception ex) {
            updateLogStatus(claim.getId(), "FAILED", "渠道受理失败");
            return -1;
        }
    }

    private void updateLogStatus(Long id, String status, String error) {
        publishLogMapper.update(null, new LambdaUpdateWrapper<CpsPublishLogDO>().eq(CpsPublishLogDO::getId, id)
                .set(CpsPublishLogDO::getStatus, status).set(CpsPublishLogDO::getErrorMsg, error));
    }

    private List<Long> resolveTargetMemberIds(CpsPublishTaskDO task) {
        if ("SPECIFIED".equals(task.getTargetType())) return parseMembers(task.getTargetIds());
        List<Long> ids = rebateAccountMapper.selectList(new LambdaQueryWrapperX<CpsRebateAccountDO>()
                .orderByAsc(CpsRebateAccountDO::getId).last("limit 1001")).stream().map(CpsRebateAccountDO::getMemberId).toList();
        if (ids.size() > 1000 || ids.stream().anyMatch(id -> id == null || id <= 0)) throw invalid("Invalid recipient list");
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private List<Long> parseMembers(String text) {
        if (!StringUtils.hasText(text)) throw invalid("Recipients required");
        String[] values = text.split(",", -1);
        if (values.length > 1000) throw invalid("At most 1000 recipients allowed");
        Set<Long> ids = new LinkedHashSet<>();
        for (String value : values) {
            try {
                long id = Long.parseLong(value.trim());
                if (id <= 0) throw invalid("Recipient IDs must be positive");
                ids.add(id);
            } catch (NumberFormatException ex) {
                throw invalid("Invalid recipient ID");
            }
        }
        return new ArrayList<>(ids);
    }

    private void validateTask(CpsPublishTaskDO task) {
        if (task == null) throw invalid("Task required");
        length(task.getTaskName(), 128, true);
        length(task.getTaskCode(), 64, false);
        length(task.getContentTemplate(), 10000, true);
        length(task.getLinkTemplate(), 2048, false);
        length(task.getTemplateCode(), 64, false);
        length(task.getCronExpression(), 128, false);
        length(task.getTargetIds(), 21000, false);
        length(task.getRemark(), 500, false);
        if (!"MANUAL".equals(task.getSourceType()) || task.getSourceId() != null) throw invalid("Only MANUAL content is supported");
        if (!channelMap.containsKey(task.getChannelCode())) throw invalid("Unsupported channel");
        if (!"CLIPBOARD".equals(task.getChannelCode())) length(task.getTemplateCode(), 64, true);
        if ("SPECIFIED".equals(task.getTargetType())) parseMembers(task.getTargetIds());
        else if (!"REBATE_MEMBER".equals(task.getTargetType())) throw invalid("Unsupported target type");
        if (task.getFrequencyLimitPerDay() != null && task.getFrequencyLimitPerDay() < 0) throw invalid("Invalid frequency");
        if (task.getStartTime() != null && task.getEndTime() != null && task.getEndTime().isBefore(task.getStartTime())) throw invalid("Invalid time window");
        if ((task.getQuietStartTime() == null) != (task.getQuietEndTime() == null)) throw invalid("Both quiet times required");
        if (StringUtils.hasText(task.getCronExpression()) && !CronExpression.isValidExpression(task.getCronExpression())) throw invalid("Invalid cron expression");
        String remaining = task.getContentTemplate().replace("{member_id}", "").replace("{task_name}", "").replace("{url}", "");
        if (remaining.contains("{") || remaining.contains("}")) throw invalid("Unsupported placeholder");
        if (StringUtils.hasText(task.getLinkTemplate())) {
            try {
                URI uri = URI.create(task.getLinkTemplate());
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null) throw invalid("Plain HTTP URL required");
            } catch (IllegalArgumentException ex) {
                throw invalid("Plain HTTP URL required; attribution placeholders are unsupported");
            }
        }
    }

    private void length(String value, int max, boolean required) {
        if (required && !StringUtils.hasText(value) || value != null && value.length() > max) throw invalid("Invalid field length");
    }

    private ServiceException invalid(String message) { return new ServiceException(400, message); }

    private boolean inQuietHours(CpsPublishTaskDO task, LocalDateTime now) {
        LocalTime start = task.getQuietStartTime();
        LocalTime end = task.getQuietEndTime();
        if (start == null || end == null || start.equals(end)) return false;
        LocalTime time = now.toLocalTime();
        return start.isBefore(end) ? !time.isBefore(start) && time.isBefore(end) : !time.isBefore(start) || time.isBefore(end);
    }

    private LocalDateTime calculateNextDispatchTime(CpsPublishTaskDO task, LocalDateTime now) {
        if (!StringUtils.hasText(task.getCronExpression())) return null;
        if (!CronExpression.isValidExpression(task.getCronExpression())) throw invalid("Invalid cron expression");
        LocalDateTime base = task.getStartTime() != null && task.getStartTime().isAfter(now) ? task.getStartTime().minusNanos(1) : now;
        LocalDateTime next = CronExpression.parse(task.getCronExpression()).next(base);
        if (next == null) throw invalid("Invalid cron expression");
        return task.getEndTime() != null && next.isAfter(task.getEndTime()) ? null : next;
    }

    private CpsPublishTaskDO getExistingTask(Long id) {
        CpsPublishTaskDO task = id == null ? null : publishTaskMapper.selectById(id);
        if (task == null) throw new ServiceException(CpsErrorCodeConstants.PUBLISH_TASK_NOT_EXISTS);
        return task;
    }

    private String resolveTaskCode(String code) {
        if (!StringUtils.hasText(code)) return "PUB" + UUID.randomUUID().toString().replace("-", "");
        if (publishTaskMapper.selectByTaskCode(code) != null) throw new ServiceException(CpsErrorCodeConstants.PUBLISH_TASK_CODE_DUPLICATE);
        return code;
    }
}
