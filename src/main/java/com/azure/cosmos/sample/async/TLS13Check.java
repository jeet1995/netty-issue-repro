package com.azure.cosmos.sample.async;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class TLS13Check {

    public static void main(String[] args) {
        // Detect if the Maven dependency tree shows an override ("(version managed from X)")
        // invoke mvn dependency:tree filtered to the artifact.
        String managedFromVersion = null;    // the original version before management override
        try {
            List<String> lines = new ArrayList<>();
            // Invoke mvn to get a minimal tree for the artifact
            Process process = new ProcessBuilder("mvn", "-Dverbose", "dependency:tree", "-Dincludes=io.netty:netty-tcnative-boringssl-static").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) {
                    lines.add(l);
                }
            }
            process.waitFor();
            for (String l : lines) {
                if (!l.contains("netty-tcnative-boringssl-static")) continue;
                int idx = l.indexOf("(version managed from");
                if (idx >= 0) {
                    // extract managed-from version inside parentheses
                    int close = l.indexOf(')', idx);
                    if (close > idx) {
                        String inside = l.substring(idx, close);
                        // expected format: (version managed from X)
                        String[] parts = inside.split(" ");
                        if (parts.length >= 4) {
                            managedFromVersion = parts[3];
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Could not parse dependency tree for tcnative overrides: " + e.getMessage());
        }

        if (managedFromVersion != null) {
            System.out.println("High Risk: TLS 1.3 may not work. Please upgrade to latest SDK or check dependencies.");
        } else {
            System.out.println("Low Risk: TLS 1.3 requirement check passed.");
        }
    }

}
