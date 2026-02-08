package ca.yorku.cmg.cnsim.bitcoin.testutils;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestTutorial {
    private static PrintWriter out;

    public static void start(String fileName) throws IOException {
        Files.createDirectories(Paths.get("target/test-tutorials"));
        out = new PrintWriter(Files.newBufferedWriter(
                Paths.get("target/test-tutorials/" + fileName)));
    }

    public static void step(String text) {
        out.println(text);
        out.println();
    }

    public static void code(String code) {
        out.println("```");
        out.println(code);
        out.println("```");
        out.println();
    }

    public static void close() {
        if (out != null) out.close();
    }
}
