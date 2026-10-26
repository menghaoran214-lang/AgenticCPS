import tempfile
import unittest
from pathlib import Path
from verify_safety_reports import verify


class ReportGateTest(unittest.TestCase):
    def check(self, xml, required=('LoginTest',)):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'module/target/surefire-reports/TEST-login.xml'
            report.parent.mkdir(parents=True)
            report.write_text(xml, encoding='utf-8')
            return verify(directory, required)

    def test_passed_test_is_counted(self):
        self.assertEqual({'LoginTest': 1}, self.check(
            '<testsuite><testcase classname="app.LoginTest" name="login"/></testsuite>'))

    def test_empty_and_skipped_suites_are_rejected(self):
        for xml in ('<testsuite/>', '<testsuite><testcase classname="LoginTest"><skipped/></testcase></testsuite>'):
            with self.subTest(xml=xml), self.assertRaises(ValueError):
                self.check(xml)

    def test_failure_or_error_cannot_be_hidden_by_passing_test(self):
        for tag in ('failure', 'error'):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                self.check('<testsuite><testcase classname="LoginTest"/>'
                           f'<testcase classname="LoginTest"><{tag}/></testcase></testsuite>')

    def test_missing_required_suite_is_rejected(self):
        with self.assertRaises(ValueError):
            self.check('<testsuite><testcase classname="LoginTest"/></testsuite>', ('LoginTest', 'FundsTest'))

    def test_missing_reports_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(ValueError):
            verify(directory, ('LoginTest',))


if __name__ == '__main__':
    unittest.main()
