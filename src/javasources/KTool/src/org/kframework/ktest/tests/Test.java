package org.kframework.ktest.tests;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.kframework.ktest.Configuration;
import org.kframework.ktest.execution.Task;
import org.kframework.utils.ListReverser;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.general.GlobalSettings;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Test implements Comparable<Test> {

    /* data read from config.xml */
    private String language;
    private String directory;
    private List<String> programsFolders;
    private List<String> resultsFolders;
    private String tag = "";
    private String unixOnlyScript;
    private boolean recursive;
    private List<String> extensions;
    private List<String> excludePrograms;
    private Map<String, String> kompileOptions = new HashMap<String, String>();
    private Map<String, String> generalKrunOptions = new HashMap<String, String>();
    private List<Program> specialPrograms = new LinkedList<Program>();
    private boolean skipPdf;
    private boolean skipKompile;
    private boolean skipPrograms;

    /* data needed for temporary stuff */
    private Document doc;
    public Element report;
    private List<Program> programs;

    public Test(String language, List<String> programsFolder,
            List<String> resultsFolder, List<String> extensions,
            List<String> excludePrograms, String homeDir) {
        this.language = language;
        this.directory = new File(language).getAbsoluteFile().getParent();
        this.programsFolders = programsFolder;
        this.resultsFolders = resultsFolder;
        this.extensions = extensions;
        this.excludePrograms = excludePrograms == null ? new LinkedList<String>()
                : excludePrograms;
        this.recursive = true;
        this.unixOnlyScript = null;

        if (resultsFolders.size() == 0) {
            String msg = "[Warning] A '--results' option was not specified.";
            System.out.println(Configuration.wrap(msg,10));
        }

        // reports
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.newDocument();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        report = getInitialElement();
        doc.appendChild(report);

        // general krun options
        generalKrunOptions.put("--output-mode", "none");
        generalKrunOptions.put("--color", "off");

        initializePrograms(homeDir);
    }

    public Test(Element test, String rootDefDir, String rootProgramsDir,
            String rootResultsDir, String homeDir) {

        init(test, rootDefDir, rootProgramsDir, rootResultsDir);

        // reports
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.newDocument();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        report = getInitialElement();
        doc.appendChild(report);

        initializePrograms(homeDir);
    }

    private void initializePrograms(String homeDir) {
        programs = new LinkedList<Program>();

        // check the existence of the results folder
        if (resultsFolders != null) {
            for (String rf : resultsFolders)
                if (rf != null && !rf.equals("") && !new File(rf).exists()) {
                    GlobalSettings.kem.register(new KException(
                            ExceptionType.WARNING, KExceptionGroup.CRITICAL,
                            "Result folder " + rf + " does not exists.",
                            "command line", "System file."));
                }

        }

        for (String programsFolder : programsFolders) {

            if (!programsFolder.equals("")
                    && !new File(programsFolder).exists()) {
                GlobalSettings.kem.register(new KException(
                        ExceptionType.WARNING, KExceptionGroup.CRITICAL,
                        "Programs folder " + programsFolder
                                + " does not exists.", "command line",
                        "System file."));
            }

            if (programsFolder.equals(""))
                return;

            List<String> allProgramPaths = searchAll(programsFolder,
                    extensions, recursive);

            for (String programPath : allProgramPaths) {
                // ignore the programs from exclude list
                boolean excluded = false;
                for (String exclude : excludePrograms)
                    if (!exclude.equals("") && programPath.contains(exclude))
                        excluded = true;
                if (excluded)
                    continue;

                Map<String, String> krunOptions = null;
                boolean special = false;
                // treat special programs
                for (Program p : specialPrograms) {
                    if (p.programPath.equals(programPath)) {
                        krunOptions = p.krunOptions;
                        special = true;
                    }
                }
                if (!special)
                    krunOptions = this.generalKrunOptions;

                String input = null;
                String output = null;
                String error = null;
                String inputFile = null, outputFile = null, errorFile = null;
                if (resultsFolders != null) {
                    for (String rf : new ListReverser<String>(resultsFolders)) {
                        if (input == null) {
                            inputFile = searchInputFile(rf, new File(programPath).getName(), recursive);
                            if (inputFile != null)
                                input = FileUtil.getFileContent(inputFile);
                        }
                        if (output == null) {
                            outputFile = searchOutputFile(rf, new File(programPath).getName());
                            if (outputFile != null)
                                output = FileUtil.getFileContent(outputFile);
                        }
                        if (error == null) {
                            errorFile = searchErrorFile(rf, new File(programPath).getName());
                            if (errorFile != null)
                                error = FileUtil.getFileContent(errorFile);
                        }
                        if (input != null && output != null && error != null) {
                            break;
                        }
                    }
                }
                if (Configuration.VERBOSE) {
                    System.out.println("Program: " + programPath);
                    System.out.println("   .in : " + inputFile);
                    System.out.println("   .out: " + outputFile);
                    System.out.println("   .err: " + errorFile);
                }

                // custom programPath
                if (!GlobalSettings.isWindowsOS()) {
                    programPath = programPath.replaceFirst(homeDir
                            + Configuration.FILE_SEPARATOR, "");
                } 
                Program p = new Program(programPath, krunOptions, this, input,
                        output, error);
                programs.add(p);

            }
        }
    }

    private String searchOutputFile(String resultsFolder2, String name) {
        return searchFile(resultsFolder2, name + Configuration.OUT, recursive);
    }

    private String searchErrorFile(String resultsFolder2, String name) {
        return searchFile(resultsFolder2, name + Configuration.ERR, recursive);
    }

    private String searchInputFile(String resultsFolder2, String name,
            boolean recursive) {
        return searchFile(resultsFolder2, name + Configuration.IN, recursive);
    }

    private String searchFile(String folder, String filename, boolean recursive) {
        String[] files = new File(folder).list();
        String file = null;
        if (files != null)
            for (String file1 : files) {

                // search in depth first
                if (recursive)
                    if (new File(folder + Configuration.FILE_SEPARATOR
                            + file1).isDirectory())
                        file = searchFile(folder + Configuration.FILE_SEPARATOR
                                + file1, filename, recursive);
                if (file != null)
                    return file;

                if (new File(folder + Configuration.FILE_SEPARATOR + file1)
                        .isFile())
                    if (file1.equals(filename))
                        file = new File(folder + Configuration.FILE_SEPARATOR
                                + file1).getAbsolutePath();
                if (file != null)
                    return file;
            }

        return file;
    }

    private List<String> searchAll(String programsFolder,
            List<String> extensions, boolean recursive) {

        if (extensions.isEmpty())
            return new LinkedList<String>();

        List<String> paths = new LinkedList<String>();
        for (String extension : extensions)
            paths.addAll(searchAll(programsFolder, extension));

        if (recursive) {
            String[] files = new File(programsFolder).list();
            if (files != null)
                for (String file : files) {
                    if (new File(programsFolder + Configuration.FILE_SEPARATOR
                            + file).isDirectory()) {
                        paths.addAll(searchAll(programsFolder
                                + Configuration.FILE_SEPARATOR + file,
                                extensions, recursive));
                    }
                }
        }

        return paths;
    }

    private List<String> searchAll(String programsFolder2, String extension) {
        String[] files = new File(programsFolder2).list();
        List<String> fls = new LinkedList<String>();
        if (files != null) {
            for (String file : files)
                if (new File(programsFolder2 + Configuration.FILE_SEPARATOR
                        + file).isFile()) {
                    if (file.endsWith(extension))
                        fls.add(programsFolder2 + Configuration.FILE_SEPARATOR
                                + file);
                }
        }
        return fls;
    }

    private void init(Element test, String rootDefDir, String rootProgramsDir,
            String rootResultsDir) {
        // get full name
        language = resolveAbsolutePathRelativeTo(
                test.getAttribute(Configuration.LANGUAGE), rootDefDir,
                Configuration.DEF_ERROR);

        directory = new File(language).getAbsoluteFile().getParent();
        if (test.hasAttribute(Configuration.DIRECTORY)) {
            directory = resolveAbsolutePathRelativeTo(
                    test.getAttribute(Configuration.DIRECTORY), directory,
                    null); // no need to exist
        }

        // programs without extensions
        if (!test.getAttribute(Configuration.PROGRAMS_DIR).trim().equals("") && test.getAttribute(Configuration.EXTENSIONS2).trim().equals("")) {
            String msg = "The 'programs' attribute requires a 'extention' attribute:  ";
            msg += "<test definition=" + test.getAttribute(Configuration.LANGUAGE) + " programs=" + test.getAttribute(Configuration.PROGRAMS_DIR) + " />";
            GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, Configuration.wrap(msg), "command line", "System file."));
        // extensions without programs
        } else if (test.getAttribute(Configuration.PROGRAMS_DIR).trim().equals("") && !test.getAttribute(Configuration.EXTENSIONS2).trim().equals("")) {
            String msg = "The 'extension' attribute requires a 'programs' attribute: ";
            msg += "<test definition=" + test.getAttribute(Configuration.LANGUAGE) + " extention=" + test.getAttribute(Configuration.EXTENSIONS2) + " />";
            GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, Configuration.wrap(msg), "command line", "System file."));
        }

        // get programs dir
        programsFolders = new LinkedList<String>();
        List<String> allpd = Arrays.asList(test.getAttribute(
                Configuration.PROGRAMS_DIR).trim().split("\\s+"));
        if (allpd.size() > 0) {
            for (String pd : allpd) {
                if (pd != null && !pd.equals("")) {
                    String p = resolveAbsolutePathRelativeTo(pd.trim(),
                            rootProgramsDir, Configuration.PGM_ERROR);
                    if (p != null){
                        programsFolders.add(p);
                    }
                }
            }
        }

        // get tests results
        resultsFolders = new LinkedList<String>();
        List<String> allrd = Arrays.asList(test.getAttribute(Configuration.RESULTS).trim().split("\\s+"));
        if (allrd.size() > 0) {
            for (String rd : allrd) {
                if (rd != null && !rd.equals("")) {
                    String p = resolveAbsolutePathRelativeTo(rd.trim(),
                            rootResultsDir, Configuration.RES_ERROR);
                    if (p != null) {
                        resultsFolders.add(p);
                    }
                }
            }
        }
        //Collections.reverse(resultsFolders);
        if (resultsFolders.size() == 0) {
            String msg = "[Warning] A 'results' attribute was not specified: ";
            msg += "<test definition=" + test.getAttribute(Configuration.LANGUAGE) + " programs=" + test.getAttribute(Configuration.PROGRAMS_DIR) + " />";
            System.out.println(Configuration.wrap(msg,10));
        }

        // get report dir
        String reportDir = resolveAbsolutePathRelativeTo(
                test.getAttribute(Configuration.REPORT_DIR), rootDefDir, "");
        if (report != null && reportDir.equals(""))
            reportDir = null;

        unixOnlyScript = test.getAttribute(Configuration.UNIX_ONLY);
        if (unixOnlyScript.equals(""))
            unixOnlyScript = null;

        // get Jenkins tag
        tag = test.getAttribute(Configuration.TITLE);

        // get skip
        if (test.hasAttribute(Configuration.SKIP_OPTION)) {
            String skip = test.getAttribute(Configuration.SKIP_OPTION);
            if (skip.contains(Configuration.KOMPILE_STEP))
                this.skipKompile = true;
            if (skip.contains(Configuration.PDF_STEP))
                this.skipPdf = true;
            if (skip.contains(Configuration.PROGRAMS_STEP))
                this.skipPrograms = true;
        }

        // set recursive
        String rec = test.getAttribute(Configuration.RECURSIVE);
        if (rec.equals("") || rec.equals(Configuration.YES))
            recursive = true;
        else
            recursive = false;

        // extensions
        extensions = Arrays.asList(test.getAttribute(Configuration.EXTENSIONS2)
                .trim().split("\\s+"));

        // exclude programs
        excludePrograms = Arrays.asList(test
                .getAttribute(Configuration.EXCLUDE).trim().split("\\s+"));

        // kompile options
        NodeList kompileOpts = test
                .getElementsByTagName(Configuration.KOMPILE_OPTION);
        for (int i = 0; i < kompileOpts.getLength(); i++) {
            Element option = (Element) kompileOpts.item(i);
            kompileOptions.put(option.getAttribute(Configuration.NAME),
                    option.getAttribute(Configuration.VALUE));
        }

        // load programs with special krun options
        NodeList specialPgms = test.getElementsByTagName(Configuration.PROGRAM);
        for (int i = 0; i < specialPgms.getLength(); i++) {
            Element pgm = (Element) specialPgms.item(i);
            String programPath = pgm.getAttribute(Configuration.NAME);
            Map<String, String> map = getKrunOptions(pgm);
            String input = null;
            String output = null;
            String error = null;
            String inputFile = null, outputFile = null, errorFile = null;
            if (resultsFolders != null) {
                for (String rf : new ListReverser<String>(resultsFolders)) {
                    if (input == null) {
                        inputFile = searchInputFile(rf, new File(programPath).getName(), recursive);
                        if (inputFile != null)
                            input = FileUtil.getFileContent(inputFile);
                    }
                    if (output == null) {
                        outputFile = searchOutputFile(rf, new File(programPath).getName());
                        if (outputFile != null)
                            output = FileUtil.getFileContent(outputFile);
                    }
                    if (error == null) {
                        errorFile = searchErrorFile(rf, new File(programPath).getName());
                        if (errorFile != null)
                            error = FileUtil.getFileContent(errorFile);
                    }
                    if (input != null && output != null && error != null) {
                        break;
                    }
                }
            }

            Program program = new Program(resolveAbsolutePathRelativeTo(
                    programPath, rootProgramsDir, Configuration.PGM_ERROR),
                    map, this, input, output, error);
            specialPrograms.add(program);
        }

        // general krun options
        NodeList genOpts = test
                .getElementsByTagName(Configuration.ALL_PROGRAMS);
        if (genOpts != null && genOpts.getLength() > 0) {
            Element all = (Element) genOpts.item(0);
            generalKrunOptions = getKrunOptions(all);
        }

        if (genOpts.getLength() == 0) {
            generalKrunOptions.put("--color", "off");
            generalKrunOptions.put("--output-mode", "none");
        }
    }

    private String resolveAbsolutePathRelativeTo(String path, String rootDir,
            String errorMessage) { // do not check existance when errorMessage is null

        if (path == null) {
            GlobalSettings.kem.register(new KException(ExceptionType.ERROR,
                    KExceptionGroup.CRITICAL,
                    "Empty attribute in configuration file.", "command line",
                    "System file."));
        }

        if (new File(path).isAbsolute())
            return new File(path).getAbsolutePath();
        else {

            if (rootDir == null) {
                GlobalSettings.kem.register(new KException(ExceptionType.ERROR,
                        KExceptionGroup.CRITICAL, "File " + rootDir
                                + " does not exists.", "command line",
                        "System file."));
            }

            File resultFile = new File(rootDir + Configuration.FILE_SEPARATOR + path);
            if (resultFile.exists() || errorMessage == null) {
                return resultFile.getAbsolutePath();
            } else {
                GlobalSettings.kem.register(new KException(ExceptionType.ERROR,
                        KExceptionGroup.CRITICAL, "File " + rootDir
                                + Configuration.FILE_SEPARATOR + path
                                + " does not exists.\n" + errorMessage,
                        "command line", "System file."));
            }
        }
        return null;
    }

    private Map<String, String> getKrunOptions(Element parent) {
        Map<String, String> map = new HashMap<String, String>();
        NodeList opts = parent.getElementsByTagName(Configuration.KRUN_OPTION);
        for (int j = 0; j < opts.getLength(); j++) {
            Element krunOpt = (Element) opts.item(j);

            // unescape < and >
            String optValue = krunOpt.getAttribute(Configuration.VALUE);
            optValue = optValue.replaceAll("&lt;", "<");
            optValue = optValue.replaceAll("&gt;", ">");

            String parserHome = krunOpt.getAttribute(Configuration.PARSER_HOME);
            String parser = System.getenv(parserHome);
            if (parser != null) {
                optValue = parser + System.getProperty("file.separator")
                        + optValue;
            }

            map.put(krunOpt.getAttribute(Configuration.NAME), optValue);
        }
        return map;
    }

    private Element getInitialElement() {
        Element testsuite = doc.createElement(Configuration.TESTSUITE);
        String name = getReportFilename().replaceFirst("-report.xml", "");
        name = name.replaceAll("\\.", "/");
        name = name.replaceFirst("/", ".");
        testsuite.setAttribute(Configuration.NAME,
                name.replaceFirst("/", "\\."));
        return testsuite;
    }

    public Element createReportElement(String testcase, String status,
            String time, String output, String error, Task task,
            String expected, boolean failureCondition) {
        Element testcaseE = doc.createElement(Configuration.TESTCASE);
        testcaseE.setAttribute(Configuration.NAME, testcase);
        testcaseE.setAttribute(Configuration.STATUS, status);
        testcaseE.setAttribute(Configuration.TIME, time);

        Element sysout = doc.createElement(Configuration.SYSTEM_OUT);
        sysout.setTextContent(output);

        Element syserr = doc.createElement(Configuration.SYSTEM_ERR);
        syserr.setTextContent(error);

        testcaseE.appendChild(syserr);
        testcaseE.appendChild(sysout);

        if (failureCondition) {
            Element error_ = doc.createElement(Configuration.ERROR);
            error_.setTextContent(task.getStderr());
            error_.setAttribute(Configuration.MESSAGE, task.getStderr());
            testcaseE.appendChild(error_);

            Element failure = doc.createElement("failure");
            failure.setTextContent("Expecting:\n" + expected
                    + "\nbut returned:\n" + task.getStdout());
            testcaseE.appendChild(failure);
        }

        return testcaseE;
    }

    public Task getUnixOnlyScriptTask(File homeDir) {
        if (unixOnlyScript == null)
            return null;
        return new Task(new String[] { unixOnlyScript }, null, homeDir);
    }

    public boolean runOnOS() {
        if (unixOnlyScript != null
                && System.getProperty("os.name").toLowerCase().contains("win")) {
            return false;
        }
        return true;
    }

    public Task getDefinitionTask(File homeDir) {
        ArrayList<String> command = new ArrayList<String>();
        command.add(Configuration.getKompile());
        command.add(language);
        command.add("--directory");
        command.add(getDirectory());
        for (Entry<String, String> entry : kompileOptions.entrySet()) {
            command.add(entry.getKey());
            command.add(entry.getValue());
        }

        String[] arguments = new String[command.size()];
        int i = 0;
        for (String cmd : command) {
            arguments[i] = cmd;
            i++;
        }

        return new Task(arguments, null, homeDir);
    }

    public boolean compiled(Task task) {
        if (task.getExit() != 0)
            return false;

        if (!new File(getCompiled()).exists())
            return false;

        if (!task.getStderr().equals(""))
            return false;

        if (!task.getStdout().equals(""))
            return false;

        return true;
    }

    public String getCompiled() {
        return getDirectory() + File.separator + FileUtil.stripExtension(new File(getLanguage()).getName()) + "-kompiled";
    }

    public String getDirectory() {
        assert directory != null;
        return directory;
            //new File(getLanguage()).getAbsoluteFile().getParent();
                //+ (tag.equals("") ? "" : "-" + tag);
    }

    private String getReportFilename() {
        String name = new File(language).getName();

        String absName = new File(language).getAbsolutePath();
        if (absName.startsWith(Configuration.USER_DIR)) {
            name = absName.substring(Configuration.USER_DIR.length() + 1);
        }

        if (!GlobalSettings.isWindowsOS()) {
            name = name.replaceAll(Configuration.FILE_SEPARATOR, ".");

        }
        
        name = name.replaceFirst("\\.k$", "-report.xml");
        if (!tag.equals(""))
            name = tag + "." + name;

        return name;
    }

    public void reportCompilation(Task task) {
        String message = compiled(task) ? "success" : "failed";
        if (!task.getStdout().equals("") || !task.getStderr().equals(""))
            if (message.equals("success"))
                message = "unstable";

        report.appendChild(createReportElement(new File(language).getName(),
                message, task.getElapsed() + "", task.getStdout(),
                task.getStderr(), task, "", !compiled(task)));
        if (Configuration.REPORT) {
            save();
        }
    }

    public void reportPdfCompilation(Task task) {
        String message = compiledPdf(task) ? "success" : "failed";
        if (!task.getStdout().equals(""))
            if (message.equals("success"))
                message = "unstable";

        report.appendChild(createReportElement(new File(getXmlLanguage())
                .getName().replaceFirst("\\.k$", ".pdf"), message,
                task.getElapsed() + "", task.getStdout(), task.getStderr(),
                task, "", !compiledPdf(task)));
        if (Configuration.REPORT)
            save();
    }

    public boolean compiledPdf(Task task) {
        if (task.getExit() != 0)
            return false;

        if (!new File(getPdfCompiledFilename()).exists())
            return false;

        if (!task.getStderr().equals(""))
            return false;

        if (!task.getStdout().equals(""))
            return false;

        return true;
    }

    private String getPdfCompiledFilename() {
        return getDirectory() + File.separator + FileUtil.stripExtension(new File(getLanguage()).getName()) + ".pdf";
    }

    public void save() {
        String reportPath = Configuration.JR + Configuration.FILE_SEPARATOR
                + getReportFilename();
        new File(Configuration.JR).mkdirs();
        try (Writer writer = new BufferedWriter(new FileWriter(reportPath))) {
            writer.write(format(doc));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String format(Document document) {
        Transformer transformer;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, Configuration.YES);

            // initialize StreamResult with File object to save to file
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(document);
            transformer.transform(source, result);
            return result.getWriter().toString();

        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerFactoryConfigurationError e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Task getPdfDefinitionTask(File homeDir) {
        ArrayList<String> command = new ArrayList<String>();
        command.add(Configuration.getKompile());
        command.add(language);
        command.add("--backend");
        command.add("pdf");
        command.add("--directory");
        command.add(getDirectory());
        String[] arguments = new String[command.size()];
        int i = 0;
        for (String cmd : command) {
            arguments[i] = cmd;
            i++;
        }

        return new Task(arguments, null, homeDir);
    }

    private String getXmlLanguage() {
        return getCompiled() + Configuration.FILE_SEPARATOR + "defx.bin";
        // return language;
    }

    public List<Program> getPrograms() {
        return programs;
    }

    public String getLanguage() {
        return language;
    }

    @Override
    public int compareTo(Test o) {
        int d;
        if (o == this)
            return 0;
        d = this.getReportFilename().compareTo(o.getReportFilename());
        if (d != 0)
            return d;
        d = (this.hashCode() == (o.hashCode())) ? 0 : 1;
        return d;
    }

    @Override
    public String toString() {
        return "[" + language + "]" + " ---> " + getReportFilename() + "\n"
                + programs + "\n\n";
    }

    public String getTag() {
        if (!tag.equals(""))
            return "(" + tag + ")";
        return "";
    }


    public boolean isSkipPdf() {
        return skipPdf;
    }

    public boolean isSkipKompile() {
        return skipKompile;
    }

    public boolean isSkipPrograms() {
        return skipPrograms;
    }

    public String getKompileOptions() {
        String kompileOption = "";
        for (Entry<String, String> entry : kompileOptions.entrySet()) {
            kompileOption += entry.getKey() + " " + entry.getValue() + " ";
        }
        return kompileOption;
    }
}
