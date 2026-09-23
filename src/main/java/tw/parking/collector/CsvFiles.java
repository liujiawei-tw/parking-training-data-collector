package tw.parking.collector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class CsvFiles {
    private CsvFiles() {}

    static void appendRows(Path file, List<String> header, List<List<String>> rows) throws IOException {
        Files.createDirectories(file.getParent());
        boolean writeHeader = Files.notExists(file) || Files.size(file) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (writeHeader) {
                writer.write(row(header));
                writer.newLine();
            }
            for (List<String> values : rows) {
                writer.write(row(values));
                writer.newLine();
            }
        }
    }

    static String row(List<String> values) {
        return values.stream().map(CsvFiles::cell).reduce((a, b) -> a + "," + b).orElse("");
    }

    static String cell(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.contains(",") || value.contains("\"") || value.contains("\r")
                || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }
}
