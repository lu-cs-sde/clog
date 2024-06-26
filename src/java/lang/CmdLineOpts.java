package lang;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import lang.io.CSVUtil;
import lang.io.FileUtil;


public class CmdLineOpts {
	private String outputDir;
	private String factsDir;
	private String outputFile;
	private String inputFile;
	private String libFile;
	private String profileFile;
	private Connection sqlDbConnection; // Internal option
	private Integer dbEntryTag = null; // Internal option
	private Action action = Action.EVAL_INTERNAL;
	private boolean warningsEnabled = false;
	private Lang lang;
	private SortedMap<String, String> srcs;
	private List<String> clangArgs = Collections.emptyList();
  private boolean debug = false;

	public enum Action {
		EVAL_SOUFFLE,
		EVAL_INTERNAL,
		EVAL_INTERNAL_PARALLEL,
		EVAL_HYBRID,
		PRETTY_SOUFFLE,
		PRETTY_INTERNAL,
		PRETTY_TYPES,
		GEN_HYBRID,
		CHECK
  }

	public enum Lang {
		C,
		C4
	}

  public void setDebugFlag(boolean enabled) {
    debug = enabled;
  }

  public boolean getDebugFlag() {
    return debug;
  }

	public void setOutputDir(String str) {
		this.outputDir = str;
	}

	public SortedMap<String, String> getSrcs() {
		return srcs;
	}

	public String getProfileFile() {
		return profileFile;
	}

	public void setProfileFile(String profileFile) {
		this.profileFile = profileFile;
	}

	public Integer getDbEntryTag() {
		return dbEntryTag;
	}

	public void setDbEntryTag(Integer dbEntryTag) {
		this.dbEntryTag = dbEntryTag;
	}

	public Connection getSqlDbConnection() {
		return sqlDbConnection;
	}

	public void setSqlDbConnection(Connection conn) {
		this.sqlDbConnection = conn;
	}

	public boolean isWarningsEnabled() {
		return warningsEnabled;
	}

	public void setWarningsEnabled(boolean warningsEnabled) {
		this.warningsEnabled = warningsEnabled;
	}

	public String getLibFile() {
		return libFile;
	}

	public void setLibFile(String libFile) {
		this.libFile = libFile;
	}

	public Action getAction() {
		return this.action;
	}

	public void setAction(Action action) {
		this.action = action;
	}

	public String getInputFile() {
		return this.inputFile;
	}

	public void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}

	public String getOutputFile() {
		return this.outputFile;
	}

	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}

	public String getOutputDir() {
		return this.outputDir;
	}

	public String getFactsDir() {
		return this.factsDir;
	}

	public void setFactsDir(String factsDir) {
		this.factsDir = factsDir;
	}

	public void setClangArgs(String args) {
		this.clangArgs = List.of(args.split("\\s+"));
	}

	public List<String> getClangArgs() {
		return this.clangArgs;
	}

	@Override public String toString() {
		String s = "";
		s += "output dir : " + outputDir + "\n";
		s += "facts dir : " + factsDir + "\n";
		s += "output file : " + outputFile + "\n";
		s += "input file : " + inputFile + "\n";
		s += "action : " + action + "\n";
		return s;
	}

	public static CmdLineOpts parseCmdLineArgs(String[] args) {
		DefaultParser parser = new DefaultParser();
		CmdLineOpts ret = new CmdLineOpts();

		Option prettyPrint = Option.builder("p").longOpt("pretty-print").numberOfArgs(1)
			.desc("Pretty print the program in MetaDL (arg = metadl) or Souffle (arg = souffle) format.").build();
		Option eval = Option.builder("e").longOpt("eval").numberOfArgs(1)
			.desc("Evaluate the program using the internal (arg = metadl), parallel (arg = metadl-par), Souffle (arg = souffle), Hybrid (arg = hybrid) evaluator.").build();
		Option check = Option.builder("c").longOpt("check").hasArg(false)
			.desc("Check that the input represents a valid MetaDL program.").build();
		Option gen = Option.builder("g").longOpt("gen-hybrid").hasArg(false)
			.desc("Generate a hybrid MetaDL-Souffle program.").build();
		Option lang = Option.builder("L").longOpt("lang").numberOfArgs(1)
			.desc("The language of the analyzed sources (arg = java or arg = metadl).").build();

		OptionGroup actions = new OptionGroup().addOption(eval).addOption(prettyPrint).addOption(check)
            .addOption(gen);

		Option factDir = Option.builder("F").longOpt("facts").numberOfArgs(1)
			.desc("Fact directory.").argName("DIR").build();
		Option outDir = Option.builder("D").longOpt("out").numberOfArgs(1)
			.desc("Output directory.").argName("DIR").build();
		Option outFile = Option.builder("o").longOpt("output").numberOfArgs(1)
			.desc("Output file.").argName("FILE").build();
		Option srcs = Option.builder("S").longOpt("sources").numberOfArgs(1)
			.desc("Source directories or files.").argName("SRCS").build();
		Option libFile = Option.builder("l").longOpt("lib").numberOfArgs(1)
			.desc("Library file to use for hybrid evaluation.").argName("FILE").build();
		Option enableWarnings = Option.builder("w").longOpt("warn").hasArg(false)
			.desc("Print warnings.").build();
		Option profile = Option.builder("P").longOpt("profile").numberOfArgs(1)
			.desc("Enable profiling and dump the results in JSON format").argName("FILE").build();
		Option clangArgs = Option.builder().longOpt("Xclang").numberOfArgs(1).valueSeparator()
			.desc("Arguments forwarded to clang").build();
    Option debug = Option.builder().longOpt("debug")
      .desc("Output all relations.").build();
		Options options = new Options().addOptionGroup(actions)
			.addOption(factDir).addOption(outDir)
			.addOption(srcs).addOption(lang)
			.addOption(outFile)
			.addOption(libFile).addOption(enableWarnings)
			.addOption(profile).addOption(clangArgs)
      .addOption(debug);



		try {
			CommandLine cmd = parser.parse(options, args);
			if (cmd.getArgs().length != 1) {
				System.err.println("Missing PROGRAM argument.");
				printHelp(options);
				throw new RuntimeException();
			} else {
				ret.setInputFile(cmd.getArgs()[0]);
			}

			if (cmd.hasOption("p")) {
				if (cmd.getOptionValue("p").equals("souffle")) {
					ret.setAction(Action.PRETTY_SOUFFLE);
				} else if (cmd.getOptionValue("p").equals("metadl")) {
					ret.setAction(Action.PRETTY_INTERNAL);
				} else if (cmd.getOptionValue("p").equals("types")) {
					ret.setAction(Action.PRETTY_TYPES);
				} else {
					System.err.println("Invalid argument to '--pretty-print' option");
					printHelp(options);
					throw new RuntimeException();
				}
			} else if (cmd.hasOption("e")) {
				if (cmd.getOptionValue("e").equals("souffle")) {
					ret.setAction(Action.EVAL_SOUFFLE);
				} else if (cmd.getOptionValue("e").equals("metadl")) {
					ret.setAction(Action.EVAL_INTERNAL);
				} else if (cmd.getOptionValue("e").equals("metadl-par")) {
					ret.setAction(Action.EVAL_INTERNAL_PARALLEL);
				} else if (cmd.getOptionValue("e").equals("hybrid")) {
					ret.setAction(Action.EVAL_HYBRID);
				} else {
					System.err.println("Invalid argument to '--eval' option");
					printHelp(options);
					throw new RuntimeException();
				}
			} else if (cmd.hasOption("c")) {
				ret.setAction(Action.CHECK);
			} else if (cmd.hasOption("g")) {
				ret.setAction(Action.GEN_HYBRID);
			}

			if (cmd.hasOption("P")) {
				ret.setProfileFile(cmd.getOptionValue("P"));
			}

      if (cmd.hasOption("debug")) {
        ret.setDebugFlag(true);
      }

			if (cmd.hasOption("S")) {
				WildcardFileFilter csvFilter = new WildcardFileFilter("*.csv");
				File csvFile = new File(cmd.getOptionValue("S"));
				if (csvFile.exists() && csvFilter.accept(csvFile)) {
					// This is a CSV file listing all the files to be analyzed
					try {
						ret.srcs = new TreeMap<>();
						CSVUtil.readMap(ret.srcs, Function.identity(), Function.identity(), csvFile.getAbsolutePath());
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				} else {
					ret.srcs = new TreeMap<>();
					for (String f : cmd.getOptionValue("S").split(":")) {
						String[] fileAndMod = f.split(",");
						if (fileAndMod.length != 2) {
							throw new RuntimeException("Error parsing the -sources [-S] argument.");
						} else {
							ret.srcs.put(fileAndMod[0], fileAndMod[1]);
						}
					}
				}
			} else {
				ret.srcs = Collections.emptySortedMap();
			}

			if (!cmd.hasOption("L") || cmd.getOptionValue("L").equals("c4")) {
				ret.lang = Lang.C4;
			} else if (cmd.getOptionValue("L").equals("c")) {
				ret.lang = Lang.C;
			} else {
				System.err.println("Unsupported language for the --lang option.");
				printHelp(options);
				throw new RuntimeException();
			}

			if (cmd.hasOption("Xclang")) {
				ret.setClangArgs(cmd.getOptionValue("Xclang"));
			}

			ret.setFactsDir(cmd.getOptionValue("F", "."));
			ret.setOutputDir(cmd.getOptionValue("D", "."));
			ret.setOutputFile(cmd.getOptionValue("o",
												 ret.getOutputDir() + "/" +
												 FileUtil.changeExtension(FileUtil.fileName(ret.getInputFile()), ".dl")));
			ret.setLibFile(cmd.getOptionValue("l", "libSwigInterface.so"));
			ret.setWarningsEnabled(cmd.hasOption("w"));
		} catch (ParseException e) {
			printHelp(options);
			throw new RuntimeException(e);
		}

		return ret;
	}

	public static void printHelp(Options options) {
		String header = "Compile and run a MetaDL program.\n\n";
		String footer = "\nPlease report issues at https://github.com/lu-cs-sde/metadl";

		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("metadl PROGRAM", header, options, footer, true);
	}

	public Lang getLang() {
		return lang;
	}

	public void setLang(Lang lang) {
		this.lang = lang;
	}
}
