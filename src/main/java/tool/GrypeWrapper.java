package tool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pique.analysis.ITool;
import pique.analysis.Tool;
import pique.model.Diagnostic;
import pique.model.Finding;
import utilities.helperFunctions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class GrypeWrapper extends Tool implements ITool {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrypeWrapper.class);
    private String githubTokenPath;

    public GrypeWrapper(String githubTokenPath) {
        super("grype", null);
        this.githubTokenPath = githubTokenPath;
    }

    // Methods
    /**
     * @param projectLocation The name of the image to analyze, with format: "name:tag"
     * @return The path to the analysis results file
     */
    @Override
    public Path analyze(Path projectLocation) {
        //workaround because grype targets images, which are loaded by docker
        String imageName = projectLocation.toString();
        LOGGER.info(this.getName() + "  Analyzing "+ imageName);
        File tempResults = new File(System.getProperty("user.dir") + "/out/grype-" + imageName + ".json");
        tempResults.delete(); // clear out the last output. May want to change this to rename rather than delete.
        tempResults.getParentFile().mkdirs();

        String[] cmd = {"grype",
                imageName,
                "--scope", "all-layers",
                "-o", "json",
                "--file",tempResults.toPath().toAbsolutePath().toString()};
        LOGGER.info(Arrays.toString(cmd));
        try {
            helperFunctions.getOutputFromProgram(cmd,LOGGER);
        } catch (IOException e) {
            LOGGER.error("Failed to run Grype");
            LOGGER.error(e.toString());
            e.printStackTrace();
        }

        return tempResults.toPath();
    }

    /**
     * parses output of tool from analyze().
     *
     * @param toolResults location of the results, output by analyze()
     * @return A Map<String,Diagnostic> with findings from the tool attached. Returns null if tool failed to run.
     */
    @Override
    public Map<String, Diagnostic> parseAnalysis(Path toolResults) {
        System.out.println(this.getName() + " Parsing Analysis...");
        LOGGER.debug(this.getName() + " Parsing Analysis...");

        Map<String, Diagnostic> diagnostics = helperFunctions.initializeDiagnostics(this.getName());

        String results = "";

        try {
            results = helperFunctions.readFileContent(toolResults);
        } catch (IOException e) {
            LOGGER.info("No results to read from Grype.");
        }

        ArrayList<String> cveList = new ArrayList<String>();
        ArrayList<Integer> severityList = new ArrayList<Integer>();

        try {
            JSONObject jsonResults = new JSONObject(results);
            JSONArray vulnerabilities = jsonResults.optJSONArray("runs").optJSONObject(0).optJSONObject("tool").optJSONObject("driver").optJSONArray("rules");

            // if vulnerabilities is null we had no findings, thus return
            if (vulnerabilities == null) {
                return diagnostics;
            }

            for (int i = 0; i < vulnerabilities.length(); i++) {
                JSONObject jsonFinding = (JSONObject) vulnerabilities.get(i);
                //Need to change this for this tool.
                String findingName = jsonFinding.get("id").toString();
                String findingSeverity = ((JSONObject) jsonFinding.get("properties")).get("security-severity").toString();
                severityList.add(this.severityToInt(findingSeverity));
                cveList.add(findingName);
            }

            // TODO: change CVE_to_CWE script to return both the CVE and CWE, do this by printing CVE,CWE then
            // in this for loop split them at the comma
            String[] findingNames = helperFunctions.getCWE(cveList, this.githubTokenPath);
            for (int i = 0; i < findingNames.length; i++) {
                Diagnostic diag = diagnostics.get((findingNames[i]+" Grype Diagnostic"));
                if (diag == null) {
                    //this means that either it is unknown, mapped to a CWE outside of the expected results, or is not assigned a CWE
                    //We may want to treat this in another way.
                    // My (Eric) CVE to CWE script handles if cwe is unknown so different node for other.
                    // unknown means we don't know the CWE for the CVE
                    // other means it is a CWE outside of our software development view
                    diag = diagnostics.get("CWE-other Grype Diagnostic");
                    LOGGER.warn("CVE with CWE outside of CWE-1000 found.");
                }
                Finding finding = new Finding("",0,0,severityList.get(i));
                finding.setName(cveList.get(i));
                diag.setChild(finding);
            }


        } catch (JSONException e) {
            LOGGER.warn("Unable to read results from Grype");
        }

        return diagnostics;
    }

    /**
     * Initializes the tool by installing it through python pip from the command line.
     */
    @Override
    public Path initialize(Path toolRoot) {
        final String[] cmd = {"grype", "version"};

        try {
            helperFunctions.getOutputFromProgram(cmd, LOGGER);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("Failed to initialize " + this.getName());
            LOGGER.error(e.getStackTrace().toString());
        }

        return toolRoot;
    }


    //maps low-critical to numeric values based on the highest value for each range.
    private Integer severityToInt(String severity) {
        Integer severityInt = 1;
        switch(severity.toLowerCase()) {
            case "low": {
                severityInt = 4;
                break;
            }
            case "medium": {
                severityInt = 7;
                break;
            }
            case "high": {
                severityInt = 9;
                break;
            }
            case "critical": {
                severityInt = 10;
                break;
            }
        }

        return severityInt;
    }
}
