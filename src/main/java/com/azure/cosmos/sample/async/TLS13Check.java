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

        // New: detect netty-handler version
        String nettyHandlerVersion = detectNettyHandlerVersion();
        if (nettyHandlerVersion != null) {
            System.out.println("Detected io.netty:netty-handler version: " + nettyHandlerVersion);
        } else {
            System.out.println("Could not determine netty-handler version (it may be brought transitively or resolution failed).");
        }

        boolean handlerTooOld = isVersionLessOrEqual(nettyHandlerVersion, "4.1.55.Final");

        if (managedFromVersion != null && handlerTooOld) {
            System.out.println("High Risk: TLS 1.3 may not work." +
                    " Please upgrade to latest SDK and ensure netty-handler >= " +
                    "4.1.56.Final.");
        } else {
            System.out.println("Low Risk: TLS 1.3 requirement check passed.");
        }
    }

    private static String detectNettyHandlerVersion() {
        try {
            List<String> lines = new ArrayList<>();
            Process p = new ProcessBuilder("mvn", "-Dverbose", "dependency:list", "-Dincludes=io.netty:netty-handler").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) { lines.add(l); }
            }
            p.waitFor();
            for (String l : lines) {
                // Lines often look like: [INFO]    io.netty:netty-handler:jar:4.1.75.Final:compile
                if (l.contains("io.netty:netty-handler:")) {
                    String[] parts = l.trim().split(":");
                    // expect ... io.netty:netty-handler:jar:VERSION:scope
                    if (parts.length >= 5) {
                        return parts[4 - 1]; // index 3 (0-based) is version
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Could not resolve netty-handler version: " + e.getMessage());
        }
        return null;
    }

    private static boolean isVersionLessOrEqual(String v, String threshold) {
        if (v == null) return false; // unknown -> don't mark as old w/out evidence
        int[] a = parseVersionTriple(v);
        int[] b = parseVersionTriple(threshold);
        for (int i = 0; i < a.length; i++) {
            if (a[i] < b[i]) return true;
            if (a[i] > b[i]) return false;
        }
        return true; // equal
    }

    private static int[] parseVersionTriple(String v) {
        // Netty format: major.minor.patch[.Qualifier] e.g. 4.1.55.Final
        String core = v;
        int q = v.indexOf('-');
        if (q > 0) core = v.substring(0, q);
        int dotFinal = core.indexOf(".Final");
        if (dotFinal > 0) core = core.substring(0, dotFinal);
        String[] segs = core.split("\\.");
        int[] out = new int[]{0,0,0};
        for (int i = 0; i < Math.min(3, segs.length); i++) {
            try { out[i] = Integer.parseInt(segs[i].replaceAll("[^0-9]", "")); } catch (NumberFormatException ignore) { }
        }
        return out;
    }
}
