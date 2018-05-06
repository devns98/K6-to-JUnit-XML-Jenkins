import groovy.json.JsonSlurper
import groovy.xml.*
import groovy.transform.TupleConstructor


def call(String inputFilePath, String outputFilePath){
    process(inputFilePath, outputFilePath)
}

@NonCPS
def process(String inputFilePath, String outputFilePath) {
    
    int totalTestsCount = 0, totalPassesCount = 0,    totalFailuresCount = 0

    def jsonSlurper = new JsonSlurper()
    def testSuites = new HashSet<TestSuite>()

    new File(inputFilePath).eachLine { line ->
        // Read input file line by line
        def jsondata = jsonSlurper.parse(line.toCharArray());
        if (jsondata.metric == "checks" && jsondata.data.tags) {
            
            def data = jsondata.data
            EntityNames entityNames = nameSplitter(data)

            def status = getStatus(data)
            def checkName = assertName(data)

            //creating Testsuites here
            def parsedTestSuite = new  TestSuite(entityNames.testSuiteName)

            //creating Testcases here
            def parsedTestCase = new TestCase(entityNames.testCaseName, entityNames.testClassName, status)

            def testSuite = testSuites.find { it.equals(parsedTestSuite) }
            if (!testSuite) {
                testSuite = parsedTestSuite
                testSuites.add(testSuite)
            }

            def testCase = testSuite.testCases.find { it.equals(parsedTestCase) }
            if (!testCase) {
                testCase = parsedTestCase
                testSuite.testCases.add(testCase)
                testSuite.testCount++
            }

            if (status == "failed") {
                testCase.failures.add(checkName)
                testSuite.failuresCount++
                totalFailuresCount++
            } else {
                totalPassesCount++
            }

            testCase.assertions++
        }
    }
    printXml(testSuites, outputFilePath)
}

@NonCPS
def printXml(testSuites, outputFilePath){
    def builder = new StreamingMarkupBuilder()
    builder.encoding = 'UTF-8'

    def testsuitesPrint = builder.bind {
        delegate.testsuites() {
            for (TestSuite testSuite : testSuites) {
                delegate.testsuite(name: testSuite.name, tests: testSuite.testCount, failures: testSuite.failuresCount) {
                    for (TestCase testCase : testSuite.testCases) {
                        delegate.testcase(name: testCase.name, assertions: testCase.assertions, classname: testCase.classname, status: testCase.status) {
                            for (String message : testCase.failures) {
                                delegate.failure(message: message, type: "Check failed")
                            }
                        }
                    }
                }
            }
        }
    }


    def outputFile = new File(outputFilePath).newWriter()
    outputFile << XmlUtil.serialize(testsuitesPrint)
    outputFile.close()
}

@NonCPS
def nameSplitter(stringInput) {
    String[] parsedNames = stringInput.tags.group.split("::")
    parsedNames = parsedNames.drop(1)
    def size = parsedNames.size()

    //Assign the index values of parsedName array to definite entities- testSuiteName, testClassName, testCaseName based on array size
    EntityNames result = new EntityNames("", "", "")
    if (size == 1) {
        result.testSuiteName = parsedNames[0]
        result.testClassName = parsedNames[0]
        result.testCaseName = parsedNames[0]
    } else if (size == 2) {
        result.testSuiteName = parsedNames[0]
        result.testClassName = parsedNames[1]
        result.testCaseName = parsedNames[1]
    } else if (size > 2) {
        StringBuffer testCaseName = new StringBuffer()
        for (i = 2; i < size; i++) {
            testCaseName.append(parsedNames[i])
        }
        result.testSuiteName = parsedNames[0]
        result.testClassName = parsedNames[1]
        result.testCaseName = testCaseName.toString()
    }
    return result
}

@NonCPS
def assertName(stringInput) {
    def checkname = stringInput.tags.check
    return checkname
}

@NonCPS
def getStatus(data) {
    String value = data.value
    if (value == "1") {
        return "passed";
    }
    if (value == "0") {
        return "failed";
    }
}

@TupleConstructor()
public class EntityNames {
    String testSuiteName
    String testClassName
    String testCaseName
}

@TupleConstructor(includes = ["name"])
public class TestSuite {
    String name
    int failuresCount
    int testCount = 0
    def testCases = new HashSet<TestCase>()

    @Override
    @NonCPS
    public boolean equals(Object o) {

        TestSuite ts = (TestSuite) o;
        return Objects.equals(name, ts.name);
    }

    @Override
    @NonCPS
    public int hashCode() {
        return Objects.hash(name);
    }
}

@TupleConstructor(includes = ["name", "classname", "status"])
public class TestCase {
    String name
    String classname
    String status
    int assertions
    def failures = []

    @Override
    @NonCPS
    public boolean equals(Object o) {

        TestCase tc = (TestCase) o;
        return Objects.equals(name, tc.name) && Objects.equals(classname, tc.classname);
    }

    @Override
    @NonCPS
    public int hashCode() {
        return Objects.hash(name, classname);
    }
}