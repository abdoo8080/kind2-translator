package edu.uiowa.cs.clc.translator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

  public static void main(String[] args) throws IOException {
    System.out.println(AntlrToLustre.parseLustreText(Files.readString(Paths.get(args[0]))));
  }
}
