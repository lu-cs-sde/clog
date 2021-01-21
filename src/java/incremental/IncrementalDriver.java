package incremental;

import java.sql.Statement;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.extendj.ast.ClassSource;
import org.extendj.ast.CompilationUnit;

import eval.EvaluationContext;
import eval.Relation2;
import lang.CmdLineOpts;
import lang.ast.AnalyzeBlock;
import lang.ast.FormalPredicate;
import lang.ast.PredicateType;
import lang.ast.Program;
import lang.ast.ProgramRepresentation;
import lang.ast.StandardPrettyPrinter;
import lang.io.CSVUtil;
import lang.io.FileUtil;
import lang.io.SQLUtil;
import lang.Compiler;
import static lang.io.SimpleLogger.*;
import static prof.Profile.*;
import lang.java.obj.DatalogProjection2;
import lang.java.obj.DatalogProjectionSink;
import lang.java.obj.FileIdDatabase;
import lang.relation.RelationWrapper;
import lang.relation.TupleInserter;
import lang.relation.RelationWrapper.TupleWrapper;

class StandaloneDatalogProjectionSink extends DatalogProjectionSink {
	@Override public DatalogProjectionSink remap(Map<FormalPredicate, TupleInserter> sinkRemap) {
		throw new RuntimeException("Remapping is not supported.");
	}

	private EnumMap<ProgramRepresentation, RelationWrapper> relations = new EnumMap<>(ProgramRepresentation.class);

	private int fileTag;
	private String relPrefix;

	private static RelationWrapper makeRelation(EvaluationContext ctx, PredicateType t) {
		return new RelationWrapper(ctx, new Relation2(t.arity()), t);
	}

	public StandaloneDatalogProjectionSink(int fileTag, String relPrefix) {
		EvaluationContext ctx = new EvaluationContext();
		this.fileTag = fileTag;
		this.relPrefix = relPrefix;
		for (ProgramRepresentation pr : ProgramRepresentation.values()) {
			relations.put(pr, makeRelation(ctx, FormalPredicate.programRepresentationType(pr)));
		}
	}

	@Override public RelationWrapper getAST() {
		return relations.get(ProgramRepresentation.AST);
	}

	@Override public RelationWrapper getProvenance() {
		return relations.get(ProgramRepresentation.ATTR_PROVENANCE);
	}

	@Override public RelationWrapper getAttributes() {
		return relations.get(ProgramRepresentation.ATTR);
	}

	@Override public RelationWrapper getSrcLoc() {
		return relations.get(ProgramRepresentation.SRC);
	}

	@Override public RelationWrapper getNTA() {
		return relations.get(ProgramRepresentation.NTA);
	}

	private void writeToDBHelper(RelationWrapper rel, File dbFile, String table) throws SQLException {
		SQLUtil.writeRelation(rel.getContext(), rel.type(), rel.getRelation(), dbFile.getPath(), table, fileTag);
	}

	public void writeToDB(File dbFile) throws SQLException {
		for (ProgramRepresentation pr : ProgramRepresentation.values()) {
			writeToDBHelper(relations.get(pr), dbFile, relPrefix + pr.getPredicateName());
		}
	}
}

public class IncrementalDriver {
	private File common;
	private File prog;
	private File srcs;

	private File fileIdDbFile;
	private File progDbFile;
	private File localSouffleProg;
	private File globalSouffleProg;
	private File updateSouffleProg;
	private File externalClassesFile;

	private ProgramSplit progSplit;

	private boolean useSouffle;

	public IncrementalDriver(File root, ProgramSplit progSplit, boolean useSouffle) {
		this.useSouffle = useSouffle;
		this.progSplit = progSplit;
		// folders
		this.common = new File(root, "common");
		this.prog = new File(root, "prog");
		this.srcs = new File(root, "srcs");
		// files
		this.fileIdDbFile = new File(common, "fileid.csv");
		// program database
		this.progDbFile = new File(srcs, "program.db");
		// external classes
		this.externalClassesFile = new File(common, "extclass.csv");
		// souffle programs
		this.localSouffleProg = new File(prog, "local");
		this.globalSouffleProg = new File(prog, "global");
		this.updateSouffleProg = new File(prog, "update");
	}

	private FileIdDatabase fileIdDb;
	private Map<String, String> externalClasses = new TreeMap<>();

	/**
	   Creates the directory structure of the cache and ensures that
	   the local, global and update programs exist.
	 * @throws SQLException
	 */
	public void init() throws IOException, SQLException {
		// ensure that all directories exist
		common.mkdir();
		prog.mkdir();
		srcs.mkdir();

		// load the file id database
		if (!progDbFile.exists()) {
			logger().info("Using a fresh file ID database");
			fileIdDb = new FileIdDatabase();
		} else {
			fileIdDb = FileIdDatabase.loadFromTable(progDbFile.getPath(), ProgramSplit.FILE_ID);
		}

		// load the external classes
		if (externalClassesFile.exists()) {
			logger().info("Loading external classes database from " + externalClassesFile);
			CSVUtil.readMap(externalClasses, Function.identity(), Function.identity(), externalClassesFile.getPath());
		}

		dumpProgram(progSplit.getLocalProgram(), new File(prog, "local.mdl"));
		dumpProgram(progSplit.getGlobalProgram(), new File(prog, "global.mdl"));
		dumpProgram(progSplit.getUpdateProgram(),  new File(prog, "update.mdl"));

		if (useSouffle) {
			// generate the Souffle executables
			if (!localSouffleProg.exists()) {
				generateSouffleProgram(progSplit.getLocalProgram(), localSouffleProg, "-j 4 -p local.profile");
			} else {
				logger().info("Using existing Souffle local program from " + localSouffleProg);
			}

			if (!globalSouffleProg.exists()) {
				generateSouffleProgram(progSplit.getGlobalProgram(), globalSouffleProg, "-j 4 -p global.profile");
			} else {
				logger().info("Using existing Souffle global program from " + globalSouffleProg);
			}

			if (!updateSouffleProg.exists()) {
				generateSouffleProgram(progSplit.getUpdateProgram(), updateSouffleProg, "-j 4 -p update.profile");
			} else {
				logger().info("Using existing Souffle update program from " + updateSouffleProg);
			}
		}
	}

	public void shutdown() throws IOException, SQLException {
		// store the file-id database
		fileIdDb.storeToTable(progDbFile.getPath(), ProgramSplit.FILE_ID);
		// store the external classes file
		CSVUtil.writeMap(externalClasses, externalClassesFile.getPath());
	}

	private void compileSouffleProgram(File src, File exec, String extraArgs) throws IOException {
		// The -j 4 argument ensures that Souffle uses OpenMP in the generated code.
		// The number of threads is selected dynamically and the value 4 is not relevant.
		String cmd = "souffle " + extraArgs + " -w " + src.getPath() + " -o " + exec.getPath();
		logger().debug("Compiling Souffle program with: " + cmd);
		FileUtil.run(cmd);
	}

	private void generateSouffleProgram(Program p, File path, String extraArgs) throws IOException {
		File tmp = new File(path.getPath() + ".dl");
		Compiler.prettyPrintSouffle(p, tmp.getPath());
		compileSouffleProgram(tmp, path, extraArgs);
	}

	private void dumpProgram(Program p, File path) throws IOException {
		StandardPrettyPrinter<Program> lpp =
			new StandardPrettyPrinter<>(new PrintStream(path));
		lpp.prettyPrint(p);
	}

	private org.extendj.ast.Program createProgram(org.extendj.ast.FileIdStorage fs) {
		org.extendj.ast.Program p = new org.extendj.ast.Program();
		// log trace events to the provenance stack machine
		p.trace().setReceiver(p.provenance);
		// set the node id source
		p.fileIdStorage = fs;

		// Set the path to the Java runtime
		String bootCP = System.getenv().get("METADL_JAVA_RT");
		if (bootCP != null)
			p.options().setValueForOption(bootCP, "-bootclasspath");
		String CP = System.getenv().get("METADL_JAVA_CP");
		if (CP != null)
			p.options().setValueForOption(CP, "-classpath");

		return p;
	}

	/**
	   Run a sanity check on the program
	 */
	private void checkProgram(org.extendj.ast.Program p) {
		// Some sanity check
		org.extendj.ast.TypeDecl object = p.lookupType("java.lang", "Object");
		if (object.isUnknown()) {
			// If we try to continue without java.lang.Object, we'll just get a stack overflow
			// in member lookups because the unknown (Object) type would be treated as circular.
			throw new RuntimeException("Error: java.lang.Object is missing."
									   + " The Java standard library was not found.");
		}
	}


	private Collection<ClassSource> generateFromSrc(File f, String relPrefix) throws IOException, SQLException {
		List<ClassSource> externalClasses = new ArrayList<>();

		profile().startTimer("object_file_compile", f.getPath());
		logger().debug("Extracting AST facts from source " + f.getPath() + ".");
		// TODO: is the Program object needed, or is the CU enough?
		org.extendj.ast.Program p = createProgram(fileIdDb);
		org.extendj.ast.CompilationUnit cu = p.addSourceFile(f.getPath());
		checkProgram(p);

		profile().stopTimer("object_file_compile", f.getPath());

		int fileTag = fileIdDb.getIdForFile(f.getPath());

		profile().startTimer("object_file_generate_relations", f.getPath());

		// generate the Datalog projection
		StandaloneDatalogProjectionSink sink = new StandaloneDatalogProjectionSink(fileTag, relPrefix);
		DatalogProjection2 proj = new DatalogProjection2(fileIdDb, p.provenance);

		Set<CompilationUnit> externalCUs = proj.generate(cu, sink);
		externalCUs.stream().filter(u -> !u.fromSource())
			.map(CompilationUnit::getClassSource).forEachOrdered(externalClasses::add);

		profile().stopTimer("object_file_generate_relations", f.getPath());

		profile().startTimer("object_file_db_write", f.getPath());
		sink.writeToDB(progDbFile);
		profile().stopTimer("object_file_db_write", f.getPath());

		return externalClasses;
	}

	private Collection<ClassSource> generateFromLib(ClassSource src, String relPrefix) throws IOException, SQLException {
		org.extendj.ast.Program externalClassProgram = createProgram(fileIdDb);

		// this class file is not in the database
		logger().debug("Adding class file to database " + src.relativeName() + ".");
		src.openInputStream();

		profile().startTimer("object_file_compile", src.relativeName());
		CompilationUnit externalCU = src.parseCompilationUnit(externalClassProgram);
		profile().stopTimer("object_file_compile", src.relativeName());
		int fileTag = fileIdDb.getIdForFile(src.relativeName());
		StandaloneDatalogProjectionSink sink = new StandaloneDatalogProjectionSink(fileTag, relPrefix);

		profile().startTimer("object_file_generate_relations", src.relativeName());
		DatalogProjection2 proj = new DatalogProjection2(fileIdDb, externalClassProgram.provenance);
		proj.generate(externalCU, sink);
		profile().stopTimer("object_file_generate_relations", src.relativeName());

		profile().startTimer("object_file_db_write", src.relativeName());
		sink.writeToDB(progDbFile);
		profile().stopTimer("object_file_db_write", src.relativeName());

		return Collections.emptyList();
	}


	/**
	   Generate the program representation for the given source files, and store it
	   to predicates with a given prefix
	 */
	private List<File> generate(List<File> sourceFiles, String relPrefix) throws IOException, SQLException {
		StopWatch timer = StopWatch.createStarted();

		Set<ClassSource> externalClasses = new TreeSet<>(new Comparator<ClassSource>() {
			@Override
			public int compare(ClassSource arg0, ClassSource arg1) {
				return arg0.relativeName().compareTo(arg1.relativeName());
			}
			});

		// sourceFiles.parallelStream().filter(File::exists).map(f -> {
		// 													  try { return generateFromSrc(f, relPrefix); }
		// 													  catch (Exception e) { throw new RuntimeException(e);}
		// 													  finally { return Collections.<ClassSource>emptyList(); }})
		// 	.forEach(externalClasses::addAll);

		for (File f : sourceFiles) {
			if (!f.exists()) {
				logger().info("Skipping file " + f + ". File does not exist. ");
				continue;
			}

			externalClasses.addAll(generateFromSrc(f, relPrefix));
		}


		// TODO: This list encodes paths inside JARs, not on the file system.
		List<File> externalFiles = new ArrayList<>();

		for (ClassSource src : externalClasses) {
			if (this.externalClasses.put(src.relativeName(), src.relativeName()) != null) {
				// check whether the external class was already analyzed
				logger().debug("Skipping external class " + src.relativeName());
				continue;
			}

			externalFiles.add(new File(src.relativeName()));

			generateFromLib(src, relPrefix);
		}

		timer.stop();
		logger().time("Fact extraction from " + sourceFiles.size() + " files: " + timer.getTime() + " ms");

		return externalFiles;
	}

	private void deleteEntries(Connection conn, String table, int fileId) throws SQLException {
		Statement stmt = conn.createStatement();
		stmt.executeUpdate("DELETE FROM '" + table + "' WHERE _tag = " + fileId);
	}

	private void delete(List<File> sourceFiles, String relPrefix) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + progDbFile.getPath());
		conn.setAutoCommit(false);

		for (File f : sourceFiles) {
			int fileId = fileIdDb.getIdForFile(f.getPath());

			for (ProgramRepresentation r : ProgramRepresentation.values()) {
				deleteEntries(conn, relPrefix + r.getPredicateName(), fileId);
			}
			for (String localTable  : progSplit.getCachedPredicates()) {
				deleteEntries(conn, localTable, fileId);
			}
		}

		conn.commit();
		conn.close();
	}

	private RelationWrapper computeAnalyzedSources(String factDir) throws IOException, SQLException {
		Program prog = progSplit.getProgram();
		CmdLineOpts opts = new CmdLineOpts();
		opts.setAction(CmdLineOpts.Action.EVAL_INTERNAL);
		opts.setFactsDir(factDir);

		// evaluate the EDB and IMPORT predicate, to ensure
		// that the inputs to the analyze blocks can be evaluated in turn
		prog.evalEDB(prog.evalCtx(), opts);
		prog.evalIMPORT(prog.evalCtx(), opts);

		// find out which predicate defines the analyzed sources
		FormalPredicate srcPred = progSplit.getSourceRelation();
		srcPred.eval(prog.evalCtx());

		// copy the tuples from one relation to another
		RelationWrapper srcWrapper = new RelationWrapper(prog.evalCtx(), srcPred.relation2(), srcPred.type());

		return srcWrapper;
	}

	private static void setRelation(Program prog, String relName, RelationWrapper rel) {
		FormalPredicate pred = prog.formalPredicateMap().get(relName);
		pred.relation2().clear();
		RelationWrapper dst = new RelationWrapper(prog.evalCtx(), pred.relation2(), pred.type());
		dst.insertTuples(rel.tuples());
	}

	private static RelationWrapper getRelation(Program prog, String relName) {
		FormalPredicate pred = prog.formalPredicateMap().get(relName);
		RelationWrapper src = new RelationWrapper(prog.evalCtx(), pred.relation2(), pred.type());
		return src;
	}

	/**
	   Update the program representation for the given files.
	 */
	public void update(CmdLineOpts opts) throws IOException, SQLException {
		RelationWrapper analyzedSrcs = computeAnalyzedSources(opts.getFactsDir());

		List<File> visitFiles;
		List<File> deletedFiles;
		if (opts.getAction() == CmdLineOpts.Action.INCREMENTAL_INIT ||
			opts.getAction() == CmdLineOpts.Action.INCREMENTAL_INIT_INTERNAL) {
			visitFiles = analyzedSrcs.tuples().stream().map(t -> new File(t.getAsString(0))).collect(Collectors.toList());
			deletedFiles = Collections.emptyList();
			logger().debug("Initial incremental run with files " + visitFiles + ".");

			profile().setCounter("file_delta", "n_D", 0);
			profile().setCounter("file_delta", "n_A", visitFiles.size());
			profile().setCounter("file_delta", "n_M", 0);
			profile().setCounter("file_delta", "n_analyzed", visitFiles.size());
		} else {
			assert opts.getAction() == CmdLineOpts.Action.INCREMENTAL_UPDATE;
			RelationWrapper visitFilesRel;
			RelationWrapper removeFilesRel;

			profile().startTimer("update_program", "total");

			if (useSouffle) {
				SQLUtil.clearRelation(progDbFile.getPath(), ProgramSplit.ANALYZED_SOURCES_RELATION);
				SQLUtil.writeRelation(progDbFile.getPath(), ProgramSplit.ANALYZED_SOURCES_RELATION, analyzedSrcs);

				// Run the update program
				String cmd = updateSouffleProg + " -D " + opts.getOutputDir() + " -F " + opts.getFactsDir()
					+ " -d " + progDbFile;
				logger().debug("Souffle command: " + cmd);
				FileUtil.run(cmd);

				// Now read back the results
				visitFilesRel = SQLUtil.readRelation(progDbFile.getPath(), ProgramSplit.AST_VISIT_RELATION,
													 ProgramSplit.getTypeForUpdateRelation(ProgramSplit.AST_VISIT_RELATION));
				removeFilesRel = SQLUtil.readRelation(progDbFile.getPath(), ProgramSplit.AST_REMOVE_RELATION,
													  ProgramSplit.getTypeForUpdateRelation(ProgramSplit.AST_REMOVE_RELATION));
			} else {
				Program update = progSplit.getUpdateProgram();

				setRelation(update, ProgramSplit.ANALYZED_SOURCES_RELATION, analyzedSrcs);

				CmdLineOpts updateOpts = new CmdLineOpts();
				updateOpts.setAction(CmdLineOpts.Action.EVAL_INTERNAL);
				updateOpts.setSqlDbFile(progDbFile.getPath());
				updateOpts.setOutputDir(".");

				// evaluate the update program
				update.eval(updateOpts);

				visitFilesRel = getRelation(update, ProgramSplit.AST_VISIT_RELATION);
				removeFilesRel = getRelation(update, ProgramSplit.AST_REMOVE_RELATION);
			}

			profile().stopTimer("update_program", "total");

			deletedFiles = removeFilesRel.tuples().stream().map(t -> new File(t.getAsString(0))).collect(Collectors.toList());
			visitFiles = visitFilesRel.tuples().stream().map(t -> new File(t.getAsString(0))).collect(Collectors.toList());

			profile().setCounter("file_delta", "n_D",
								 analyzedSrcs.tuples().stream().filter(t -> t.getAsString(1).equals("D")).count());
			profile().setCounter("file_delta", "n_A",
								 analyzedSrcs.tuples().stream().filter(t -> t.getAsString(1).equals("A")).count());
			profile().setCounter("file_delta", "n_M",
								 analyzedSrcs.tuples().stream().filter(t -> t.getAsString(1).equals("M")).count());
			profile().setCounter("file_delta", "n_analyzed", visitFilesRel.tuples().size());

			logger().debug("Update incremental run with modified/added files " +
						   visitFiles + ", removed files " + deletedFiles + ".");
		}

		List<File> externalFiles = Collections.emptyList();

		// populate the program representation relations for each analyze block
		for (AnalyzeBlock b : progSplit.getProgram().analyzeBlocks()) {
			if (opts.getAction() == CmdLineOpts.Action.INCREMENTAL_UPDATE) {
				// remove the information for the deleted files, but also for the files
				// that need revisiting
				delete(Stream.concat(deletedFiles.stream(), visitFiles.stream()).collect(Collectors.toList()),
					   b.getContext().scopePrefix);
			}
			// extract information from the other files
			externalFiles = generate(visitFiles, b.getContext().scopePrefix);
		}

		runLocalProgram(Stream.concat(visitFiles.stream(), externalFiles.stream()).collect(Collectors.toList()), opts);
	}

	private void runLocalProgram(List<File> visitFiles, CmdLineOpts opts) throws IOException, SQLException {
		profile().startTimer("local_program", "total");

		StopWatch timer = StopWatch.createStarted();
		for (File file : visitFiles) {
			runLocalProgram(opts, file);
		}

		profile().stopTimer("local_program", "total");
		timer.stop();
		logger().time("Running the local program on " + visitFiles.size() + " files:" + timer.getTime() + " ms");
	}

	/**
	   Run the local program on all the files in the file database
	 */
	private void runLocalProgram(CmdLineOpts opts, File file) throws IOException, SQLException {
		logger().debug("Running the local program on " + file + ".");

		int fileId = fileIdDb.getIdForFile(file.getPath());

		StopWatch timer = StopWatch.createStarted();
		profile().startTimer("local_program", file.getPath());
		if (useSouffle) {
			String cmd = localSouffleProg.getPath() + " -D " + opts.getOutputDir() + " -F " + opts.getFactsDir() +
				" -d " + progDbFile + " -t " + fileId + "";
			logger().debug("Souffle command: " + cmd);
			FileUtil.run(cmd);
		} else {
			Program local = progSplit.getLocalProgram();
			CmdLineOpts internalOpts = new CmdLineOpts();
			internalOpts.setAction(CmdLineOpts.Action.EVAL_INTERNAL);
			internalOpts.setSqlDbFile(progDbFile.getPath());
			internalOpts.setFactsDir(opts.getFactsDir());
			internalOpts.setOutputDir(opts.getOutputDir());
			internalOpts.setDbEntryTag(fileId);

			Compiler.checkProgram(local, internalOpts);
			local.eval(internalOpts);
			local.clearRelations();
		}
		timer.stop();
		profile().stopTimer("local_program", file.getPath());

		logger().time("Running local program on " + file + ":" + timer.getTime() + " ms");
	}

	public void runGlobalProgram(CmdLineOpts opts) throws SQLException, IOException {
		logger().debug("Running the global program");

		StopWatch timer = StopWatch.createStarted();
		profile().startTimer("global_program", "total");
		if (useSouffle) {
			String cmd = globalSouffleProg.getPath() + " -D " + opts.getOutputDir()  + " -F " + opts.getFactsDir() +
				" -d " + progDbFile;
			logger().debug("Souffle command: " + cmd);
			FileUtil.run(cmd);
		} else {
			Program global = progSplit.getGlobalProgram();
			CmdLineOpts internalOpts = new CmdLineOpts();
			internalOpts.setAction(CmdLineOpts.Action.EVAL_INTERNAL);
			internalOpts.setSqlDbFile(progDbFile.getPath());
			internalOpts.setOutputDir(opts.getOutputDir());
			internalOpts.setFactsDir(opts.getFactsDir());
			Compiler.checkProgram(global, internalOpts);
			global.eval(internalOpts);
		}
		timer.stop();
		profile().stopTimer("global_program", "total");

		logger().time("Running the global program: " + timer.getTime() + " ms.");

	}
}
