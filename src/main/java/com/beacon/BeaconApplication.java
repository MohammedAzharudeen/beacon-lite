package com.beacon;

import com.beacon.cli.ScanCommand;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. {@code java -jar beacon.jar} starts the web app; {@code java -jar beacon.jar scan
 * <store>} runs a one-off scan and writes the Insight Brief.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BeaconApplication {

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("scan")) {
      System.exit(ScanCommand.run(Arrays.copyOfRange(args, 1, args.length), System.out));
    }
    SpringApplication.run(BeaconApplication.class, args);
  }
}
