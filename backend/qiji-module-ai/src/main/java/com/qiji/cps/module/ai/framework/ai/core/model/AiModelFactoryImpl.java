package com.qiji.cps.module.ai.framework.ai.core.model;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.lang.func.Func0;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.qiji.cps.framework.common.util.spring.SpringUtils;
import com.qiji.cps.module.ai.enums.model.AiPlatformEnum;
import com.qiji.cps.module.ai.framework.ai.config.AiAutoConfiguration;
import com.qiji.cps.module.ai.framework.ai.config.QijiAiProperties;
import com.qiji.cps.module.ai.framework.ai.core.model.baichuan.BaiChuanChatModel;
import com.qiji.cps.module.ai.framework.ai.core.model.doubao.DouBaoChatModel;
import com.qiji.cps.module.ai.framework.ai.core.model.gemini.GeminiChatModel;
import com.qiji.cps.module.ai.framework.ai.core.model.hunyuan.HunYuanChatModel;
import com.qiji.cps.module.ai.framework.ai.core.model.midjourney.api.MidjourneyApi;
import com.qiji.cps.module.ai.framework.ai.core.model.siliconflow.SiliconFlowApiConstants;
import com.qiji.cps.module.ai.framework.ai.core.model.siliconflow.SiliconFlowChatModel;
import com.qiji.cps.module.ai.framework.ai.core.model.siliconflow.SiliconFlowImageApi;
import com.qiji.cps.module.ai.framework.ai.core.model.siliconflow.SiliconFlowImageModel;
import com.qiji.cps.module.ai.framework.ai.core.model.suno.api.SunoApi;
import com.qiji.cps.module.ai.framework.ai.core.model.xinghuo.XingHuoChatModel;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.micrometer.observation.ObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.SneakyThrows;
import org.springaicommunity.moonshot.MoonshotChatModel;
import org.springaicommunity.moonshot.MoonshotChatOptions;
import org.springaicommunity.moonshot.api.MoonshotApi;
import org.springaicommunity.qianfan.QianFanChatModel;
import org.springaicommunity.qianfan.QianFanEmbeddingModel;
import org.springaicommunity.qianfan.QianFanEmbeddingOptions;
import org.springaicommunity.qianfan.QianFanImageModel;
import org.springaicommunity.qianfan.api.QianFanApi;
import org.springaicommunity.qianfan.api.QianFanImageApi;
import org.springaicommunity.qianfan.autoconfigure.QianFanChatAutoConfiguration;
import org.springaicommunity.qianfan.autoconfigure.QianFanEmbeddingAutoConfiguration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.stabilityai.autoconfigure.StabilityAiImageAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.stabilityai.StabilityAiImageModel;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientConnectionDetails;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreProperties;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import redis.clients.jedis.RedisClient;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.qiji.cps.framework.common.util.collection.CollectionUtils.convertList;

/**
 * AI Model 模型工厂的实现类
 *
 * @author AgenticCPS源码
 */
public class AiModelFactoryImpl implements AiModelFactory {

    @Override
    public ChatModel getOrCreateChatModel(AiPlatformEnum platform, String apiKey, String url) {
        String cacheKey = buildClientCacheKey(ChatModel.class, platform, apiKey, url);
        return Singleton.get(cacheKey, (Func0<ChatModel>) () -> {
            // noinspection EnhancedSwitchMigration
            switch (platform) {
                case TONG_YI:
                    return buildTongYiChatModel(apiKey);
                case YI_YAN:
                    return buildYiYanChatModel(apiKey);
                case DEEP_SEEK:
                    return buildDeepSeekChatModel(apiKey);
                case DOU_BAO:
                    return buildDouBaoChatModel(apiKey);
                case HUN_YUAN:
                    return buildHunYuanChatModel(apiKey, url);
                case SILICON_FLOW:
                    return buildSiliconFlowChatModel(apiKey);
                case ZHI_PU:
                case MINI_MAX:
                    throw unsupportedProvider(platform);
                case MOONSHOT:
                    return buildMoonshotChatModel(apiKey, url);
                case XING_HUO:
                    return buildXingHuoChatModel(apiKey);
                case BAI_CHUAN:
                    return buildBaiChuanChatModel(apiKey);
                case OPENAI:
                    return buildOpenAiChatModel(apiKey, url);
                case AZURE_OPENAI:
                    return buildOpenAiChatModel(apiKey, url);
                case ANTHROPIC:
                    return buildAnthropicChatModel(apiKey, url);
                case GEMINI:
                    return buildGeminiChatModel(apiKey);
                case OLLAMA:
                    return buildOllamaChatModel(url);
                case GROK:
                    return buildGrokChatModel(apiKey,url);
                default:
                    throw new IllegalArgumentException(StrUtil.format("未知平台({})", platform));
            }
        });
    }

    @Override
    public ChatModel getDefaultChatModel(AiPlatformEnum platform) {
        // noinspection EnhancedSwitchMigration
        switch (platform) {
            case TONG_YI:
                return SpringUtil.getBean(DashScopeChatModel.class);
            case YI_YAN:
                return SpringUtil.getBean(QianFanChatModel.class);
            case DEEP_SEEK:
                return SpringUtil.getBean(DeepSeekChatModel.class);
            case DOU_BAO:
                return SpringUtil.getBean(DouBaoChatModel.class);
            case HUN_YUAN:
                return SpringUtil.getBean(HunYuanChatModel.class);
            case SILICON_FLOW:
                return SpringUtil.getBean(SiliconFlowChatModel.class);
            case ZHI_PU:
            case MINI_MAX:
                throw unsupportedProvider(platform);
            case MOONSHOT:
                return SpringUtil.getBean(MoonshotChatModel.class);
            case XING_HUO:
                return SpringUtil.getBean(XingHuoChatModel.class);
            case BAI_CHUAN:
                return SpringUtil.getBean(BaiChuanChatModel.class);
            case OPENAI:
                return SpringUtil.getBean(OpenAiChatModel.class);
            case AZURE_OPENAI:
                return SpringUtil.getBean(OpenAiChatModel.class);
            case ANTHROPIC:
                return SpringUtil.getBean(AnthropicChatModel.class);
            case GEMINI:
                return SpringUtil.getBean(GeminiChatModel.class);
            case OLLAMA:
                return SpringUtil.getBean(OllamaChatModel.class);
            default:
                throw new IllegalArgumentException(StrUtil.format("未知平台({})", platform));
        }
    }

    @Override
    public ImageModel getDefaultImageModel(AiPlatformEnum platform) {
        // noinspection EnhancedSwitchMigration
        switch (platform) {
            case TONG_YI:
                return SpringUtil.getBean(DashScopeImageModel.class);
            case YI_YAN:
                return SpringUtil.getBean(QianFanImageModel.class);
            case ZHI_PU:
                throw unsupportedProvider(platform);
            case SILICON_FLOW:
                return SpringUtil.getBean(SiliconFlowImageModel.class);
            case OPENAI:
                return SpringUtil.getBean(OpenAiImageModel.class);
            case STABLE_DIFFUSION:
                return SpringUtil.getBean(StabilityAiImageModel.class);
            default:
                throw new IllegalArgumentException(StrUtil.format("未知平台({})", platform));
        }
    }

    @Override
    public ImageModel getOrCreateImageModel(AiPlatformEnum platform, String apiKey, String url) {
        // noinspection EnhancedSwitchMigration
        switch (platform) {
            case TONG_YI:
                return buildTongYiImagesModel(apiKey);
            case YI_YAN:
                return buildQianFanImageModel(apiKey);
            case ZHI_PU:
                throw unsupportedProvider(platform);
            case OPENAI:
                return buildOpenAiImageModel(apiKey, url);
            case SILICON_FLOW:
                return buildSiliconFlowImageModel(apiKey,url);
            case STABLE_DIFFUSION:
                return buildStabilityAiImageModel(apiKey, url);
            default:
                throw new IllegalArgumentException(StrUtil.format("未知平台({})", platform));
        }
    }

    @Override
    public MidjourneyApi getOrCreateMidjourneyApi(String apiKey, String url) {
        String cacheKey = buildClientCacheKey(MidjourneyApi.class, AiPlatformEnum.MIDJOURNEY.getPlatform(), apiKey,
                url);
        return Singleton.get(cacheKey, (Func0<MidjourneyApi>) () -> {
            QijiAiProperties.Midjourney properties = SpringUtil.getBean(QijiAiProperties.class)
                    .getMidjourney();
            return new MidjourneyApi(url, apiKey, properties.getNotifyUrl());
        });
    }

    @Override
    public SunoApi getOrCreateSunoApi(String apiKey, String url) {
        String cacheKey = buildClientCacheKey(SunoApi.class, AiPlatformEnum.SUNO.getPlatform(), apiKey, url);
        return Singleton.get(cacheKey, (Func0<SunoApi>) () -> new SunoApi(url));
    }

    @Override
    @SuppressWarnings("EnhancedSwitchMigration")
    public EmbeddingModel getOrCreateEmbeddingModel(AiPlatformEnum platform, String apiKey, String url, String model) {
        String cacheKey = buildClientCacheKey(EmbeddingModel.class, platform, apiKey, url, model);
        return Singleton.get(cacheKey, (Func0<EmbeddingModel>) () -> {
            switch (platform) {
                case TONG_YI:
                    return buildTongYiEmbeddingModel(apiKey, model);
                case YI_YAN:
                    return buildYiYanEmbeddingModel(apiKey, model);
                case ZHI_PU:
                    throw unsupportedProvider(platform);
                case MINI_MAX:
                    throw unsupportedProvider(platform);
                case OPENAI:
                    return buildOpenAiEmbeddingModel(apiKey, url, model);
                case AZURE_OPENAI:
                    return buildOpenAiEmbeddingModel(apiKey, url, model);
                case OLLAMA:
                    return buildOllamaEmbeddingModel(url, model);
                default:
                    throw new IllegalArgumentException(StrUtil.format("未知平台({})", platform));
            }
        });
    }

    @Override
    public VectorStore getOrCreateVectorStore(Class<? extends VectorStore> type,
                                              EmbeddingModel embeddingModel,
                                              Map<String, Class<?>> metadataFields) {
        String cacheKey = buildClientCacheKey(VectorStore.class, embeddingModel, type);
        return Singleton.get(cacheKey, (Func0<VectorStore>) () -> {
            if (type == SimpleVectorStore.class) {
                return buildSimpleVectorStore(embeddingModel);
            }
            if (type == QdrantVectorStore.class) {
                return buildQdrantVectorStore(embeddingModel);
            }
            if (type == RedisVectorStore.class) {
                return buildRedisVectorStore(embeddingModel, metadataFields);
            }
            if (type == MilvusVectorStore.class) {
                return buildMilvusVectorStore(embeddingModel);
            }
            throw new IllegalArgumentException(StrUtil.format("未知类型({})", type));
        });
    }

    private static String buildClientCacheKey(Class<?> clazz, Object... params) {
        if (ArrayUtil.isEmpty(params)) {
            return clazz.getName();
        }
        return StrUtil.format("{}#{}", clazz.getName(), ArrayUtil.join(params, "_"));
    }

    // ========== 各种创建 spring-ai 客户端的方法 ==========

    /**
     * Build the DashScope chat model directly to avoid the Boot 3-only auto-configuration.
     */
    private static DashScopeChatModel buildTongYiChatModel(String key) {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(key).build();
        DashScopeChatOptions options = DashScopeChatOptions.builder().model(DashScopeApi.DEFAULT_CHAT_MODEL)
                .temperature(0.7).build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .toolCallingManager(getToolCallingManager())
                .build();
    }

    /**
     * Build the DashScope image model directly to avoid the Boot 3-only auto-configuration.
     */
    private static DashScopeImageModel buildTongYiImagesModel(String key) {
        DashScopeImageApi dashScopeImageApi = DashScopeImageApi.builder().apiKey(key).build();
        return DashScopeImageModel.builder()
                .dashScopeApi(dashScopeImageApi)
                .build();
    }

    /**
     * 可参考 {@link QianFanChatAutoConfiguration} 的 qianFanChatModel 方法
     */
    private static QianFanChatModel buildYiYanChatModel(String key) {
        // TODO spring ai qianfan 有 bug，无法使用 https://github.com/spring-ai-community/qianfan/issues/6
        List<String> keys = StrUtil.split(key, '|');
        Assert.equals(keys.size(), 2, "YiYanChatClient 的密钥需要 (appKey|secretKey) 格式");
        String appKey = keys.get(0);
        String secretKey = keys.get(1);
        QianFanApi qianFanApi = new QianFanApi(appKey, secretKey);
        return new QianFanChatModel(qianFanApi);
    }

    /**
     * 可参考 {@link QianFanEmbeddingAutoConfiguration} 的 qianFanImageModel 方法
     */
    private QianFanImageModel buildQianFanImageModel(String key) {
        // TODO spring ai qianfan 有 bug，无法使用 https://github.com/spring-ai-community/qianfan/issues/6
        List<String> keys = StrUtil.split(key, '|');
        Assert.equals(keys.size(), 2, "YiYanChatClient 的密钥需要 (appKey|secretKey) 格式");
        String appKey = keys.get(0);
        String secretKey = keys.get(1);
        QianFanImageApi qianFanApi = new QianFanImageApi(appKey, secretKey);
        return new QianFanImageModel(qianFanApi);
    }

    /**
     * 可参考 {@link DeepSeekChatAutoConfiguration} 的 deepSeekChatModel 方法
     */
    private static DeepSeekChatModel buildDeepSeekChatModel(String apiKey) {
        DeepSeekApi deepSeekApi = DeepSeekApi.builder().apiKey(apiKey).build();
        DeepSeekChatOptions options = DeepSeekChatOptions.builder().model(DeepSeekApi.DEFAULT_CHAT_MODEL)
                .temperature(0.7).build();
        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .options(options)
                .toolCallingManager(getToolCallingManager())
                .build();
    }

    /**
     * 可参考 {@link AiAutoConfiguration#douBaoChatClient(QijiAiProperties)}
     */
    private ChatModel buildDouBaoChatModel(String apiKey) {
        QijiAiProperties.DouBao properties = new QijiAiProperties.DouBao()
                .setApiKey(apiKey);
        return new AiAutoConfiguration().buildDouBaoChatClient(properties);
    }

    /**
     * 可参考 {@link AiAutoConfiguration#hunYuanChatClient(QijiAiProperties)}
     */
    private ChatModel buildHunYuanChatModel(String apiKey, String url) {
        QijiAiProperties.HunYuan properties = new QijiAiProperties.HunYuan()
                .setBaseUrl(url).setApiKey(apiKey);
        return new AiAutoConfiguration().buildHunYuanChatClient(properties);
    }

    /**
     * 可参考 {@link AiAutoConfiguration#siliconFlowChatClient(QijiAiProperties)}
     */
    private ChatModel buildSiliconFlowChatModel(String apiKey) {
        QijiAiProperties.SiliconFlow properties = new QijiAiProperties.SiliconFlow()
                .setApiKey(apiKey);
        return new AiAutoConfiguration().buildSiliconFlowChatClient(properties);
    }

    /**
     * Build the Moonshot model directly because the community starter's
     * auto-configuration targets Spring Boot 3 and is excluded on Boot 4.
     */
    private MoonshotChatModel buildMoonshotChatModel(String apiKey, String url) {
        MoonshotApi.Builder moonshotApiBuilder = MoonshotApi.builder()
                .apiKey(apiKey);
        if (StrUtil.isNotEmpty(url)) {
            moonshotApiBuilder.baseUrl(url);
        }
        MoonshotChatOptions options = MoonshotChatOptions.builder().model(MoonshotApi.DEFAULT_CHAT_MODEL).build();
        return MoonshotChatModel.builder()
                .moonshotApi(moonshotApiBuilder.build())
                .defaultOptions(options)
                .toolCallingManager(getToolCallingManager())
                .build();
    }

    /**
     * 可参考 {@link AiAutoConfiguration#xingHuoChatClient(QijiAiProperties)}
     */
    private static XingHuoChatModel buildXingHuoChatModel(String key) {
        List<String> keys = StrUtil.split(key, '|');
        Assert.equals(keys.size(), 2, "XingHuoChatClient 的密钥需要 (appKey|secretKey) 格式");
        QijiAiProperties.XingHuo properties = new QijiAiProperties.XingHuo()
                .setAppKey(keys.get(0)).setSecretKey(keys.get(1));
        return new AiAutoConfiguration().buildXingHuoChatClient(properties);
    }

    /**
     * 可参考 {@link AiAutoConfiguration#baiChuanChatClient(QijiAiProperties)}
     */
    private BaiChuanChatModel buildBaiChuanChatModel(String apiKey) {
        QijiAiProperties.BaiChuan properties = new QijiAiProperties.BaiChuan()
                .setApiKey(apiKey);
        return new AiAutoConfiguration().buildBaiChuanChatClient(properties);
    }

    /**
     * 可参考 {@link OpenAiChatAutoConfiguration} 的 openAiChatModel 方法
     */
    private static OpenAiChatModel buildOpenAiChatModel(String openAiToken, String url) {
        url = StrUtil.blankToDefault(url, "https://api.openai.com");
        return OpenAiChatModel.builder()
                .openAiClient(openAiClient(url, openAiToken))
                .build();
    }

    /**
     * 可参考 {@link AzureOpenAiChatAutoConfiguration}
     */
    /**
     * 可参考 {@link AnthropicChatAutoConfiguration} 的 anthropicApi 方法
     */
    private static AnthropicChatModel buildAnthropicChatModel(String apiKey, String url) {
        AnthropicChatOptions.Builder optionsBuilder = AnthropicChatOptions.builder()
                .apiKey(apiKey);
        if (StrUtil.isNotEmpty(url)) {
            optionsBuilder.baseUrl(url);
        }
        return AnthropicChatModel.builder()
                .options(optionsBuilder.build())
                .build();
    }

    /**
     * 可参考 {@link AiAutoConfiguration#buildGeminiChatClient(QijiAiProperties.Gemini)}
     */
    private static GeminiChatModel buildGeminiChatModel(String apiKey) {
        QijiAiProperties.Gemini properties = SpringUtil.getBean(QijiAiProperties.class)
                .getGemini().setApiKey(apiKey);
        return new AiAutoConfiguration().buildGeminiChatClient(properties);
    }

    /**
     * 可参考 {@link OpenAiImageAutoConfiguration} 的 openAiImageModel 方法
     */
    private OpenAiImageModel buildOpenAiImageModel(String openAiToken, String url) {
        url = StrUtil.blankToDefault(url, "https://api.openai.com");
        return OpenAiImageModel.builder().openAiClient(openAiClient(url, openAiToken)).build();
    }

    /**
     * 创建 SiliconFlowImageModel 对象
     */
    private SiliconFlowImageModel buildSiliconFlowImageModel(String apiToken, String url) {
        url = StrUtil.blankToDefault(url, SiliconFlowApiConstants.DEFAULT_BASE_URL);
        SiliconFlowImageApi openAiApi = new SiliconFlowImageApi(url, apiToken);
        return new SiliconFlowImageModel(openAiApi);
    }

    /**
     * 可参考 {@link OllamaChatAutoConfiguration} 的 ollamaChatModel 方法
     */
    private static OllamaChatModel buildOllamaChatModel(String url) {
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(url).build();
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .build();
    }

    /**
     * 可参考 {@link StabilityAiImageAutoConfiguration} 的 stabilityAiImageModel 方法
     */
    private StabilityAiImageModel buildStabilityAiImageModel(String apiKey, String url) {
        url = StrUtil.blankToDefault(url, StabilityAiApi.DEFAULT_BASE_URL);
        StabilityAiApi stabilityAiApi = new StabilityAiApi(apiKey, StabilityAiApi.DEFAULT_IMAGE_MODEL, url);
        return new StabilityAiImageModel(stabilityAiApi);
    }

    private ChatModel buildGrokChatModel(String apiKey,String url) {
        QijiAiProperties.Grok properties = new QijiAiProperties.Grok()
                .setBaseUrl(url)
                .setApiKey(apiKey);
        return new AiAutoConfiguration().buildGrokChatClient(properties);
    }

    // ========== 各种创建 EmbeddingModel 的方法 ==========

    /**
     * Build the DashScope embedding model directly to avoid the Boot 3-only auto-configuration.
     */
    private DashScopeEmbeddingModel buildTongYiEmbeddingModel(String apiKey, String model) {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey).build();
        DashScopeEmbeddingOptions dashScopeEmbeddingOptions = DashScopeEmbeddingOptions.builder().model(model).build();
        return new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED, dashScopeEmbeddingOptions);
    }

    /**
     * 可参考 {@link QianFanEmbeddingAutoConfiguration} 的 qianFanEmbeddingModel 方法
     */
    private QianFanEmbeddingModel buildYiYanEmbeddingModel(String key, String model) {
        List<String> keys = StrUtil.split(key, '|');
        Assert.equals(keys.size(), 2, "YiYanChatClient 的密钥需要 (appKey|secretKey) 格式");
        String appKey = keys.get(0);
        String secretKey = keys.get(1);
        QianFanApi qianFanApi = new QianFanApi(appKey, secretKey);
        QianFanEmbeddingOptions qianFanEmbeddingOptions = QianFanEmbeddingOptions.builder().model(model).build();
        return new QianFanEmbeddingModel(qianFanApi, MetadataMode.EMBED, qianFanEmbeddingOptions);
    }

    private OllamaEmbeddingModel buildOllamaEmbeddingModel(String url, String model) {
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(url).build();
        OllamaEmbeddingOptions ollamaOptions = OllamaEmbeddingOptions.builder().model(model).build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .options(ollamaOptions)
                .build();
    }

    /**
     * 可参考 {@link OpenAiEmbeddingAutoConfiguration} 的 openAiEmbeddingModel 方法
     */
    private OpenAiEmbeddingModel buildOpenAiEmbeddingModel(String openAiToken, String url, String model) {
        url = StrUtil.blankToDefault(url, "https://api.openai.com");
        OpenAiEmbeddingOptions openAiEmbeddingProperties = OpenAiEmbeddingOptions.builder().model(model).build();
        return OpenAiEmbeddingModel.builder().openAiClient(openAiClient(url, openAiToken))
                .metadataMode(MetadataMode.EMBED).options(openAiEmbeddingProperties).build();
    }

    /**
     * 可参考 {@link AzureOpenAiEmbeddingAutoConfiguration} 的 azureOpenAiEmbeddingModel 方法
     */
    private static OpenAIClient openAiClient(String url, String apiKey) {
        return OpenAIOkHttpClient.builder().baseUrl(url).apiKey(apiKey).build();
    }

    private static IllegalArgumentException unsupportedProvider(AiPlatformEnum platform) {
        return new IllegalArgumentException("Provider " + platform + " is not available in Spring AI 2.0.1; migrate its vendor SDK before enabling it");
    }

    // ========== 各种创建 VectorStore 的方法 ==========

    /**
     * 注意：仅适合本地测试使用，生产建议还是使用 Qdrant、Milvus 等
     */
    @SneakyThrows
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private SimpleVectorStore buildSimpleVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        // 启动加载
        File file = new File(StrUtil.format("{}/vector_store/simple_{}.json",
                FileUtil.getUserHomePath(), embeddingModel.getClass().getSimpleName()));
        if (!file.exists()) {
            FileUtil.mkParentDirs(file);
            file.createNewFile();
        } else if (file.length() > 0) {
            vectorStore.load(file);
        }
        // 定时持久化，每分钟一次
        Timer timer = new Timer("SimpleVectorStoreTimer-" + file.getAbsolutePath());
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                vectorStore.save(file);
            }

        }, Duration.ofMinutes(1).toMillis(), Duration.ofMinutes(1).toMillis());
        // 关闭时，进行持久化
        RuntimeUtil.addShutdownHook(() -> vectorStore.save(file));
        return vectorStore;
    }

    /**
     * 参考 {@link QdrantVectorStoreAutoConfiguration} 的 vectorStore 方法
     */
    @SneakyThrows
    private QdrantVectorStore buildQdrantVectorStore(EmbeddingModel embeddingModel) {
        QdrantVectorStoreAutoConfiguration configuration = new QdrantVectorStoreAutoConfiguration();
        QdrantVectorStoreProperties properties = SpringUtil.getBean(QdrantVectorStoreProperties.class);
        // 参考 QdrantVectorStoreAutoConfiguration 实现，创建 QdrantClient 对象
        QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(
                properties.getHost(), properties.getPort(), properties.isUseTls());
        if (StrUtil.isNotEmpty(properties.getApiKey())) {
            grpcClientBuilder.withApiKey(properties.getApiKey());
        }
        QdrantClient qdrantClient = new QdrantClient(grpcClientBuilder.build());
        // 创建 QdrantVectorStore 对象
        QdrantVectorStore vectorStore = configuration.vectorStore(embeddingModel, properties, qdrantClient,
                getObservationRegistry(), getCustomObservationConvention(), getBatchingStrategy());
        // 初始化索引
        vectorStore.afterPropertiesSet();
        return vectorStore;
    }

    /**
     * 参考 {@link RedisVectorStoreAutoConfiguration} 的 vectorStore 方法
     */
    private RedisVectorStore buildRedisVectorStore(EmbeddingModel embeddingModel,
                                                   Map<String, Class<?>> metadataFields) {
        // 创建 JedisPooled 对象
        DataRedisProperties redisProperties = SpringUtils.getBean(DataRedisProperties.class);
        RedisClient redisClient = RedisClient.create(redisProperties.getUrl());
        // 创建 RedisVectorStoreProperties 对象
        RedisVectorStoreProperties properties = SpringUtil.getBean(RedisVectorStoreProperties.class);
        RedisVectorStore redisVectorStore = RedisVectorStore.builder(redisClient, embeddingModel)
                .indexName(properties.getIndexName()).prefix(properties.getPrefix())
                .initializeSchema(properties.isInitializeSchema())
                .metadataFields(convertList(metadataFields.entrySet(), entry -> {
                    String fieldName = entry.getKey();
                    Class<?> fieldType = entry.getValue();
                    if (Number.class.isAssignableFrom(fieldType)) {
                        return RedisVectorStore.MetadataField.numeric(fieldName);
                    }
                    if (Boolean.class.isAssignableFrom(fieldType)) {
                        return RedisVectorStore.MetadataField.tag(fieldName);
                    }
                    return RedisVectorStore.MetadataField.text(fieldName);
                }))
                .observationRegistry(getObservationRegistry().getObject())
                .customObservationConvention(getCustomObservationConvention().getObject())
                .batchingStrategy(getBatchingStrategy())
                .build();
        // 初始化索引
        redisVectorStore.afterPropertiesSet();
        return redisVectorStore;
    }

    /**
     * 参考 {@link MilvusVectorStoreAutoConfiguration} 的 vectorStore 方法
     */
    @SneakyThrows
    private MilvusVectorStore buildMilvusVectorStore(EmbeddingModel embeddingModel) {
        MilvusVectorStoreAutoConfiguration configuration = new MilvusVectorStoreAutoConfiguration();
        // 获取配置属性
        MilvusVectorStoreProperties serverProperties = SpringUtil.getBean(MilvusVectorStoreProperties.class);
        MilvusServiceClientProperties clientProperties = SpringUtil.getBean(MilvusServiceClientProperties.class);

        // 创建 MilvusServiceClient 对象
        MilvusServiceClient milvusClient = configuration.milvusClient(serverProperties, clientProperties,
                new MilvusServiceClientConnectionDetails() {

                    @Override
                    public String getHost() {
                        return clientProperties.getHost();
                    }

                    @Override
                    public int getPort() {
                        return clientProperties.getPort();
                    }

                }
        );
        // 创建 MilvusVectorStore 对象
        MilvusVectorStore vectorStore = configuration.vectorStore(milvusClient, embeddingModel, serverProperties,
                getBatchingStrategy(), getObservationRegistry(), getCustomObservationConvention());

        // 初始化索引
        vectorStore.afterPropertiesSet();
        return vectorStore;
    }

    private static ObjectProvider<ObservationRegistry> getObservationRegistry() {
        return new ObjectProvider<>() {

            @Override
            public ObservationRegistry getObject() throws BeansException {
                return SpringUtil.getBean(ObservationRegistry.class);
            }

        };
    }

    private static ObjectProvider<VectorStoreObservationConvention> getCustomObservationConvention() {
        return new ObjectProvider<>() {

            @Override
            public VectorStoreObservationConvention getObject() throws BeansException {
                return new DefaultVectorStoreObservationConvention();
            }

        };
    }

    private static BatchingStrategy getBatchingStrategy() {
        return SpringUtil.getBean(BatchingStrategy.class);
    }

    private static ToolCallingManager getToolCallingManager() {
        return SpringUtil.getBean(ToolCallingManager.class);
    }

    private static ObjectProvider<EmbeddingModelObservationConvention> getEmbeddingModelObservationConvention() {
        return new ObjectProvider<>() {

            @Override
            public EmbeddingModelObservationConvention getObject() throws BeansException {
                return SpringUtil.getBean(EmbeddingModelObservationConvention.class);
            }

        };
    }

}
