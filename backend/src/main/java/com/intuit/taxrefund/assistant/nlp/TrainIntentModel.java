package com.intuit.taxrefund.assistant.nlp;

import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** * Utility to train an OpenNLP document categorization model for intent classification.
 * Expects training data in the format: "intent\ttext" (tab-separated).
 * Writes the trained model to a binary file that can be loaded by OpenNLPDocumentCategorizer.
 * mvn -q -DskipTests test-compile exec:java \
 *   -Dexec.mainClass=com.intuit.taxrefund.assistant.nlp.TrainIntentModel
 */
public class TrainIntentModel {
    public static void main(String[] args) throws Exception {
        String trainPath = "src/main/resources/nlp/intent.train";
        String outPath = "src/main/resources/nlp/intent-model.bin";

        InputStreamFactory isf = new MarkableFileInputStreamFactory(new File(trainPath));

        try (
            ObjectStream<String> lineStream = new PlainTextByLineStream(isf, StandardCharsets.UTF_8);
            ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)
        ) {
            TrainingParameters params = new TrainingParameters();
            params.put(TrainingParameters.ITERATIONS_PARAM, "100");
            params.put(TrainingParameters.CUTOFF_PARAM, "1");

            DoccatFactory factory = new DoccatFactory();
            DoccatModel model = DocumentCategorizerME.train("en", sampleStream, params, factory);

            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(outPath))) {
                model.serialize(modelOut);
            }

            System.out.println("Wrote model to: " + outPath);
        }
    }
}