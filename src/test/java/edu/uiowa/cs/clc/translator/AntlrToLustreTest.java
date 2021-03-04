package edu.uiowa.cs.clc.translator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import edu.uiowa.cs.clc.kind2.api.Kind2Api;

public class AntlrToLustreTest {
  @Test
  public void benchmarksTest() throws IOException {
    Files.find(Paths.get("synthesis-benchmarks"), Integer.MAX_VALUE,
        (p, y) -> p.toString().endsWith(".lus")).forEach(this::pr);
  }

  public void pr(Path path) {
    try {
      Kind2Api api = new Kind2Api();
      api.setOnlyParse(true);
      api.execute(AntlrToLustre.parseLustreText(Files.readString(path)));
    } catch (Exception e) {
      System.out.println(path);
      e.printStackTrace();
    }
  }
}
