package runnable;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pique.analysis.ITool;
import pique.evaluation.Project;
import pique.runnable.ASingleProjectEvaluator;
import pique.utility.PiqueProperties;
import tool.GrypeWrapper;
import tool.TrivyWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SingleProjectEvaluator extends ASingleProjectEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleProjectEvaluator.class);

    //default properties location
    @Getter
    @Setter
    private String propertiesLocation = "src/main/resources/pique-properties.properties";

    public SingleProjectEvaluator(){
        init("input/projects");
    }

    public SingleProjectEvaluator(String projectToAnalyze) {
        init(projectToAnalyze);
    }

    public void init(String projectLocation){
        LOGGER.info("Starting Analysis");
        Properties prop = null;
        try {
            prop = propertiesLocation==null ? PiqueProperties.getProperties() : PiqueProperties.getProperties(propertiesLocation);
        } catch (IOException e) {
            e.printStackTrace();
        }


        Path projectRoot = Paths.get(projectLocation);
        Path resultsDir = Paths.get(prop.getProperty("results.directory"));

        LOGGER.info("Project to analyze: " + projectRoot.toString());

        Path qmLocation = Paths.get(prop.getProperty("derived.qm"));

        ITool gyrpeWrapper = new GrypeWrapper(prop.getProperty("github-token-path"));
        ITool trivyWrapper = new TrivyWrapper(prop.getProperty("github-token-path"));
        Set<ITool> tools = Stream.of(gyrpeWrapper,trivyWrapper).collect(Collectors.toSet());

        Set<Path> projectRoots = new HashSet<>();
        File[] filesToAssess = projectRoot.toFile().listFiles();
        for (File f : filesToAssess){
            if (f.isFile() && !f.getName().equals(".gitignore")){
                //skip .gitignore file which is included for convenience of packaging/distributing
                projectRoots.add(f.toPath());
            }
        }
        for (Path projectPath : projectRoots){
            Path outputPath = runEvaluator(projectPath, resultsDir, qmLocation, tools);
            LOGGER.info("output: " + outputPath.getFileName());
            System.out.println("output: " + outputPath.getFileName());
            System.out.println("exporting compact: " + project.exportToJson(resultsDir, true));
        }
    }
    //region Get / Set
    public Project getEvaluatedProject() {
        return project;
    }


}
