package lang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import lang.ast.Program;
import lang.ast.config.Description;
import lang.io.CSVUtil;
import lang.io.FileUtil;
import lang.io.SimpleLogger;
import lang.relation.Relation;

public class EvaluationTest {
	/*
	 * Compare one evaluation scheme against another.
	 */
	public void doEvaluationTest(Description d1, Description d2) throws Exception {
		Program program1 = (Program) FileUtil.parse(new File(d1.getInput().getPath()));
		d1.evaluationMethod().evaluate(program1, d1);

		Program program2 = (Program) FileUtil.parse(new File(d2.getInput().getPath()));
		d2.evaluationMethod().evaluate(program2, d2);

		program1.getFormalPredicates().forEach(fp -> {
			if(!fp.literal().hasExtendedSemantics()) {
				File in1 = new File(d1.outputDir() + "/" + fp.predicateName() + ".csv");
				Relation r1 = CSVUtil.readRelationFrom(in1, fp.realArity());
				
				SimpleLogger.logger().log("Read r1 from: " + in1.getPath(), SimpleLogger.LogLevel.Level.DEBUG);
				if (r1 == null) {
					SimpleLogger.logger().log("r1 is null: " + in1.getPath(), SimpleLogger.LogLevel.Level.ERROR);
					assertTrue(false);
				}
	
				File in2 = new File(d2.outputDir() + "/" + fp.predicateName() + ".csv");
				SimpleLogger.logger().log("Read r2 from: " + in2.getPath(), SimpleLogger.LogLevel.Level.DEBUG);
				Relation r2 = CSVUtil.readRelationFrom(in2, fp.realArity());
				if (r2 == null) {
					SimpleLogger.logger().log("r2 is null: " + in2.getPath(), SimpleLogger.LogLevel.Level.ERROR);
					assertTrue(false);
				}
	
				SimpleLogger.logger().log("r1: " + r1.tuples(), SimpleLogger.LogLevel.Level.DEBUG);
				SimpleLogger.logger().log("r2: " + r2.tuples(), SimpleLogger.LogLevel.Level.DEBUG);
				assertTrue(r1.equals(r2));
			}
		});
	}

	@DisplayName("Compare Internal Evaluation to Souffle")
	@ParameterizedTest(name = "Evaluation Tests Valid")
	@ValueSource(strings = { "evalTest_1.in", "evalTest_2.in", "evalTest_3.in", "evalTest_4.in", "evalTest_5.in",
			"evalTest_6.in", "evalTest_7.in", "evalTest_8.in", "evalTest_9.in" })
	void evaluationTestsBottomUpNaiveCompareSouffle(String fileName) throws Exception {
		Description d1 = FileUtil.parseDescription(
				"eval::souffle      -OUT ./tests/output/souffle ./tests/evaluation/" + fileName);
		Description d2 = FileUtil.parseDescription(
				"eval::bottomupnaive -OUT ./tests/output         ./tests/evaluation/" + fileName);
		doEvaluationTest(d1, d2);
	}

	@DisplayName("Compare Internal Evaluation to Souffle WithEDB")
	@ParameterizedTest(name = "Evaluation Tests Valid WithEDB")
	@ValueSource(strings = { "evalTest_1.in" })
	void evaluationTestsBottomUpNaiveCompareSouffleWithEDBs(String fileName) throws Exception {
		String outname = FileUtil.changeExtension(fileName, "_with_edb.dl");
		Description d1 = FileUtil.parseDescription(
				"eval::souffle      -OUT ./tests/output/souffle -FACTS ./tests/evaluation/withedbs/ -SOUFFLEOUT "
						+ outname + " ./tests/evaluation/withedbs/" + fileName);
		Description d2 = FileUtil.parseDescription(
				"eval::bottomupnaive -OUT ./tests/output         -FACTS ./tests/evaluation/withedbs/ ./tests/evaluation/withedbs/"
						+ fileName);
		doEvaluationTest(d1, d2);
	}
	
	@DisplayName("Compare Internal Evaluation to Souffle WithNEG")
	@ParameterizedTest(name = "Evaluation Tests Valid WithNEG")
	@ValueSource(strings = { "evalTest_1.in", "evalTest_2.in", "evalTest_3.in" })
	void evaluationTestsBottomUpNaiveCompareSouffleWithNEGs(String fileName) throws Exception {
		String outname = FileUtil.changeExtension(fileName, "_with_neg.dl");
		Description d1 = FileUtil.parseDescription(
				"eval::souffle      -OUT ./tests/output/souffle -FACTS ./tests/evaluation/withneg/ -SOUFFLEOUT "
						+ outname + " ./tests/evaluation/withneg/" + fileName);
		Description d2 = FileUtil.parseDescription(
				"eval::bottomupnaive -OUT ./tests/output         -FACTS ./tests/evaluation/withneg/ ./tests/evaluation/withneg/"
						+ fileName);
		doEvaluationTest(d1, d2);
	}
	
	@DisplayName("Compare Internal Evaluation to Souffle WithNEG")
	@ParameterizedTest(name = "Evaluation Tests Invalid WithNEG")
	@ValueSource(strings = { "evalTest_3.in" })
	void evaluationTestsBottomUpNaiveCompareSouffleWithNEGsInvalid(String fileName) throws Exception {
		String outname = FileUtil.changeExtension(fileName, "_with_neg.dl");
		Description d1 = FileUtil.parseDescription(
				"eval::souffle      -OUT ./tests/output/souffle -FACTS ./tests/evaluation/withneg/ -SOUFFLEOUT "
						+ outname + " ./tests/evaluation/withneg/" + fileName);
		Description d2 = FileUtil.parseDescription(
				"eval::bottomupnaive -OUT ./tests/output         -FACTS ./tests/evaluation/withneg/ ./tests/evaluation/withneg/"
						+ fileName);
		doEvaluationTest(d1, d2);
	}
}
