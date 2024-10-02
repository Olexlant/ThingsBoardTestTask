package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Base64;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;

public class Converter {

    private static String input = "{\n" +
            " \"deviceInfo\": {\n" +
            "   \"deviceName\": \"A1\",\n" +
            "   \"deviceProfileName\": \"P1\",\n" +
            "   \"devEui\": \"1000000000000001\"\n" +
            " },\n" +
            " \"time\": \"2023-05-22T07:47:05.404859+00:00\",\n" +
            " \"data\": \"AUVdAiIOTARoIA==\"\n" +
            "}\n";


    public static void main(String[] args) throws JsonProcessingException {
        var inputObject = (ObjectNode) new ObjectMapper().readTree(input);
        System.out.println(convert(inputObject));
    }

    public static ObjectNode convert(ObjectNode input) {
        JsonNodeFactory factory = JsonNodeFactory.instance;

        // JSON output
        ObjectNode output = factory.objectNode();

        // Get device info
        ObjectNode deviceInfo = (ObjectNode) input.get("deviceInfo");
        String deviceName = deviceInfo.get("deviceName").asText();
        String deviceProfileName = deviceInfo.get("deviceProfileName").asText();
        String devEui = deviceInfo.get("devEui").asText();

        // Put device name and type
        output.put("deviceName", deviceName);
        output.put("deviceType", deviceProfileName);

        // Set attributes
        ObjectNode attributes = factory.objectNode();
        attributes.put("devEui", devEui);
        output.set("attributes", attributes);

        // Get the timestamp and converting it to unix timestamp
        String time = input.get("time").asText();
        long timestamp = Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(time))
                .getLong(ChronoField.INSTANT_SECONDS) * 1000
                + Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(time))
                .getLong(ChronoField.MILLI_OF_SECOND);

        // Decoding Base64 data
        String data = input.get("data").asText();
        byte[] decodedBytes = Base64.getDecoder().decode(data);

        Integer battery = null;
        Double temperature = null;
        Integer humidity = null;

        // Parsing bytes by channels
        int i = 0;
        while (i < decodedBytes.length) {
            int channelId = decodedBytes[i++] & 0xFF;
            int channelType = decodedBytes[i++] & 0xFF;

            switch (channelId) {
                case 0x01: // Battery
                    if (channelType == 0x45) {
                        battery = decodedBytes[i++] & 0xFF;
                    }
                    break;
                case 0x02: // Temperature
                    if (channelType == 0x22) {
                        int tempRaw = (decodedBytes[i++] & 0xFF) << 8 | (decodedBytes[i++] & 0xFF);
                        temperature = tempRaw / 100.0;
                    }
                    break;
                case 0x04: // Humidity
                    if (channelType == 0x68) {
                        humidity = decodedBytes[i++] & 0xFF;
                    }
                    break;
            }
        }

        // Telemetry data
        ObjectNode telemetry = factory.objectNode();
        telemetry.put("ts", timestamp);

        // Put values for telemetry
        ObjectNode values = factory.objectNode();
        if (battery != null) {
            values.put("battery", battery);
        }
        if (temperature != null) {
            values.put("temperature", temperature);
        }
        if (humidity != null) {
            values.put("humidity", humidity);
        }
        telemetry.set("values", values);

        // Add telemetry to output JSON
        output.set("telemetry", telemetry);

        return output;
    }
}
