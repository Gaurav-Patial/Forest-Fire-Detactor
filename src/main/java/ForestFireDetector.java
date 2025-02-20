import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ForestFireDetector extends JFrame {
    private static final String API_KEY = "Add API_Key";
    private static final String WEATHER_API_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String AQI_API_URL = "http://api.openweathermap.org/data/2.5/air_pollution";

    private JLabel temperatureLabel;
    private JLabel humidityLabel;
    private JLabel windLabel;
    private JLabel aqiLabel;
    private JLabel statusLabel;
    private JTextField locationField;
    private JTextArea historyArea;
    private List<String> historicalData = new ArrayList<>();
    private JButton settingsButton;
    private RiskThreshold thresholds = new RiskThreshold(35, 35, 15, 50);

    public ForestFireDetector() {
        initializeUI();
        loadHistoricalData();
    }

    private void initializeUI() {
        setTitle("Forest Fire Detector");
        setSize(850, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
     //   setBackground(new Color(100,4,5));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Input Panel
        JPanel inputPanel = new JPanel(new FlowLayout());
        inputPanel.setBackground(Color.LIGHT_GRAY);
        locationField = new JTextField(20);
        locationField.setBackground(new Color(180, 193, 209, 194));
        JButton searchButton = new JButton("Search");
        searchButton.setBackground(new Color(192, 218, 227, 255));
        searchButton.addActionListener(e -> handleLocationSearch());
        inputPanel.add(new JLabel("Location:"));
        inputPanel.add(locationField);
        inputPanel.add(searchButton);

        // Data Display Panel
        JPanel dataPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        dataPanel.setBackground(new Color(182, 163, 156));
        temperatureLabel = createStyledLabel("Temperature: Loading...");
        humidityLabel = createStyledLabel("Humidity: Loading...");
        windLabel = createStyledLabel("Wind Speed: Loading...");
        aqiLabel = createStyledLabel("Air Quality: Loading...");
        statusLabel = createStyledLabel("Status: Initializing...");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        dataPanel.add(temperatureLabel);
        dataPanel.add(humidityLabel);
        dataPanel.add(windLabel);
        dataPanel.add(aqiLabel);
        dataPanel.add(statusLabel);

        // History Panel
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBackground(new Color(209, 194, 180, 220));
        historyArea = new JTextArea(10, 30);
        historyArea.setBackground(new Color(180, 193, 209, 194));
        historyArea.setEditable(false);
        historyPanel.add(new JScrollPane(historyArea), BorderLayout.CENTER);
        historyPanel.setBorder(BorderFactory.createTitledBorder("Historical Data"));

        // Settings Button
        settingsButton = new JButton("Settings");
        settingsButton.setBackground(new Color(180, 193, 209, 194));
        settingsButton.addActionListener(e -> showSettingsDialog());

        add(inputPanel, BorderLayout.NORTH);
        add(dataPanel, BorderLayout.CENTER);
        add(historyPanel, BorderLayout.EAST);
        add(settingsButton, BorderLayout.SOUTH);
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 18));
        label.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        return label;
    }

    public void fetchAndUpdateWeather() {
        String location = locationField.getText().trim();
        if (location.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a location");
            return;
        }

        try {
            WeatherData weather = fetchWeatherData(location);
            AirQualityData aqi = fetchAirQualityData(weather.lat, weather.lon);
            updateDisplay(weather, aqi);
            assessFireRisk(weather, aqi);
            saveHistoricalData(weather, aqi);
        } catch (IOException | URISyntaxException ex) {
            handleApiError(ex);
        }
    }

    private WeatherData fetchWeatherData(String location) throws IOException, URISyntaxException {
        String urlString = String.format("%s?q=%s&appid=%s&units=metric",
                WEATHER_API_URL, location.replace(" ", "%20"), API_KEY);
        JsonObject response = fetchApiResponse(urlString);

        JsonObject main = response.getAsJsonObject("main");
        JsonObject wind = response.getAsJsonObject("wind");
        JsonObject coord = response.getAsJsonObject("coord");

        return new WeatherData(
                main.get("temp").getAsDouble(),
                main.get("humidity").getAsInt(),
                wind.get("speed").getAsDouble(),
                coord.get("lat").getAsDouble(),
                coord.get("lon").getAsDouble()
        );
    }

    private AirQualityData fetchAirQualityData(double lat, double lon) throws IOException, URISyntaxException {
        String urlString = String.format("%s?lat=%f&lon=%f&appid=%s",
                AQI_API_URL, lat, lon, API_KEY);
        JsonObject response = fetchApiResponse(urlString);

        JsonObject list = response.getAsJsonArray("list").get(0).getAsJsonObject();
        int aqi = list.getAsJsonObject("main").get("aqi").getAsInt();
        JsonObject components = list.getAsJsonObject("components");

        return new AirQualityData(
                aqi,
                components.get("co").getAsDouble(),
                components.get("pm2_5").getAsDouble()
        );
    }

    private JsonObject fetchApiResponse(String urlString) throws IOException, URISyntaxException {
        URL url = new URI(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200) {
            throw new IOException("API request failed: " + connection.getResponseMessage());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    private void updateDisplay(WeatherData weather, AirQualityData aqi) {
        temperatureLabel.setText(String.format("Temperature: %.1f°C", weather.temp));
        humidityLabel.setText(String.format("Humidity: %d%%", weather.humidity));
        windLabel.setText(String.format("Wind Speed: %.1f m/s", weather.windSpeed));
        aqiLabel.setText(String.format("Air Quality Index: %d (PM2.5: %.1f µg/m³", aqi.aqi, aqi.pm25));
    }

    private void assessFireRisk(WeatherData weather, AirQualityData aqi) {
        boolean tempRisk = weather.temp > thresholds.tempThreshold;
        boolean humidityRisk = weather.humidity < thresholds.humidityThreshold;
        boolean windRisk = weather.windSpeed > thresholds.windThreshold;
        boolean aqiRisk = aqi.pm25 > thresholds.pm25Threshold;

        int riskFactors = 0;
        if (tempRisk) riskFactors++;
        if (humidityRisk) riskFactors++;
        if (windRisk) riskFactors++;
        if (aqiRisk) riskFactors++;

        String status;
        Color color;
        if (riskFactors >= 3) {
            status = "CRITICAL FIRE RISK!";
            color = Color.RED;
            triggerAlarm();
        } else if (riskFactors >= 2) {
            status = "High Fire Risk";
            color = Color.ORANGE;
        } else if (riskFactors >= 1) {
            status = "Moderate Risk";
            color = Color.YELLOW;
        } else {
            status = "Normal Conditions";
            color = Color.GREEN;
        }

        statusLabel.setText("Status: " + status);
        statusLabel.setForeground(color);
    }

    private void triggerAlarm() {
        Toolkit.getDefaultToolkit().beep();
        JOptionPane.showMessageDialog(this,
                "Critical Fire Risk Detected!",
                "Alert",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showSettingsDialog() {
        JPanel panel = new JPanel(new GridLayout(4, 2));
        JTextField tempField = new JTextField(String.valueOf(thresholds.tempThreshold));
        JTextField humidityField = new JTextField(String.valueOf(thresholds.humidityThreshold));
        JTextField windField = new JTextField(String.valueOf(thresholds.windThreshold));
        JTextField pm25Field = new JTextField(String.valueOf(thresholds.pm25Threshold));

        panel.add(new JLabel("Temperature Threshold (°C):"));
        panel.add(tempField);
        panel.add(new JLabel("Humidity Threshold (%):"));
        panel.add(humidityField);
        panel.add(new JLabel("Wind Speed Threshold (m/s):"));
        panel.add(windField);
        panel.add(new JLabel("PM2.5 Threshold (µg/m³):"));
        panel.add(pm25Field);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Risk Threshold Settings", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            try {
                thresholds.tempThreshold = Double.parseDouble(tempField.getText());
                thresholds.humidityThreshold = Double.parseDouble(humidityField.getText());
                thresholds.windThreshold = Double.parseDouble(windField.getText());
                thresholds.pm25Threshold = Double.parseDouble(pm25Field.getText());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid input values");
            }
        }
    }

    private void handleLocationSearch() {
        fetchAndUpdateWeather();
    }

    private void saveHistoricalData(WeatherData weather, AirQualityData aqi) {
        String entry = String.format("[%s] Temp: %.1f°C, Humidity: %d%%, AQI: %d",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                weather.temp, weather.humidity, aqi.aqi);
        historicalData.add(entry);
        historyArea.setText(String.join("\n", historicalData));
    }

    private void loadHistoricalData() {
        // Implement data loading from file/database
    }

    private void handleApiError(Exception ex) {
        statusLabel.setText("Status: Error fetching data");
        statusLabel.setForeground(Color.RED);
        JOptionPane.showMessageDialog(this,
                "Error accessing weather data: " + ex.getMessage(),
                "API Error",
                JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ForestFireDetector detector = new ForestFireDetector();
            detector.setVisible(true);

            Timer timer = new Timer(300000, e -> detector.fetchAndUpdateWeather());
            timer.start();
            detector.fetchAndUpdateWeather();
        });
    }

    private static class WeatherData {
        double temp;
        int humidity;
        double windSpeed;
        double lat;
        double lon;

        WeatherData(double temp, int humidity, double windSpeed, double lat, double lon) {
            this.temp = temp;
            this.humidity = humidity;
            this.windSpeed = windSpeed;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static class AirQualityData {
        int aqi;
        double co;
        double pm25;

        AirQualityData(int aqi, double co, double pm25) {
            this.aqi = aqi;
            this.co = co;
            this.pm25 = pm25;
        }
    }

    private static class RiskThreshold {
        double tempThreshold;
        double humidityThreshold;
        double windThreshold;
        double pm25Threshold;

        RiskThreshold(double temp, double humidity, double wind, double pm25) {
            this.tempThreshold = temp;
            this.humidityThreshold = humidity;
            this.windThreshold = wind;
            this.pm25Threshold = pm25;
        }
    }
}

















/*import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ForestFireDetector extends JFrame {
    // Replace with your actual API key and desired city name
    private static final String API_KEY = "https://api.openweathermap.org/data/2.5/weather?q=Hoshiarpur&appid=https://api.openweathermap.org/data/2.5/weather?lat=34.05&lon=-118.25&units=metric&appid=YOUR_KEY&units=metric\n";
    private static final String CITY = "Hoshiarpur";

    private JLabel temperatureLabel;
    private JLabel humidityLabel;
    private JLabel statusLabel;

    public ForestFireDetector() {
        setTitle("Forest Fire Detector");
        setSize(750, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(3, 1, 10, 10));

        // Initialize labels
        temperatureLabel = new JLabel("Temperature: Loading...");
        humidityLabel = new JLabel("Humidity: Loading...");
        statusLabel = new JLabel("Status: Normal");

        // Add labels to the frame
        add(temperatureLabel);
        add(humidityLabel);
        add(statusLabel);
    }

    // Method to fetch weather data from OpenWeatherMap API
    public void fetchAndUpdateWeather() {
        try {
            // Construct the API URL
            String urlString = "https://api.openweathermap.org/data/2.5/weather?q=" + CITY +
                    "&appid=" + API_KEY + "&units=metric";
            URL url = new URL(urlString);

            // Open HTTP connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Check if the connection is successful
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read API response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                // Parse JSON response and update the GUI
                parseAndUpdate(response.toString());
            } else {
                System.out.println("GET request failed. Response Code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to parse JSON response and update GUI components
    private void parseAndUpdate(String jsonResponse) {
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

        // Extract temperature and humidity from the JSON object
        JsonObject main = jsonObject.getAsJsonObject("main");
        double temperature = main.get("temp").getAsDouble();
        int humidity = main.get("humidity").getAsInt();

        // Update labels
        temperatureLabel.setText("Temperature: " + temperature + "°C");
        humidityLabel.setText("Humidity: " + humidity + "%");

        // Simple logic to determine fire risk
        if (temperature > 30 && humidity < 40) {
            statusLabel.setText("Status: FIRE RISK!");
            statusLabel.setForeground(Color.RED);
        } else {
            statusLabel.setText("Status: Normal");
            statusLabel.setForeground(Color.BLACK);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ForestFireDetector detector = new ForestFireDetector();
            detector.setVisible(true);

            // Create a Timer to update weather data every 60 seconds (60000 ms)
            Timer timer = new Timer(60000, e -> detector.fetchAndUpdateWeather());
            timer.start();

            // Fetch initial weather data
            detector.fetchAndUpdateWeather();
        });
    }
}


 */
