import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FileUploader {

    private static final String UPLOAD_URL = "https://api.anonfiles.com/upload";
    private static final String DEFAULT_SAVE_PATH = System.getProperty("user.home") + "/Desktop/urls.txt";

    private List<String> fileUrls;
    private JTextArea textArea;
    private JProgressBar progressBar;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            FileUploader uploader = new FileUploader();
            uploader.createAndShowUI();
        });
    }


    private void createAndShowUI() {
        // Set Dark Mode Look and Feel
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("File Uploader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Color.DARK_GRAY);
        mainPanel.setLayout(new BorderLayout(10, 10));

        textArea = new JTextArea(10, 30);
        textArea.setBackground(Color.DARK_GRAY);
        textArea.setForeground(Color.WHITE);
        JScrollPane scrollPane = new JScrollPane(textArea);

        textArea.addMouseListener(new UrlMouseListener());

        JButton uploadButton = new JButton("Datei(en) hochladen");
        JButton copyButton = new JButton("In Zwischenablage kopieren");
        JButton saveButton = new JButton("URLs speichern");

        uploadButton.setBackground(Color.LIGHT_GRAY);
        copyButton.setBackground(Color.LIGHT_GRAY);
        saveButton.setBackground(Color.LIGHT_GRAY);

        uploadButton.setForeground(Color.BLACK);
        copyButton.setForeground(Color.BLACK);
        saveButton.setForeground(Color.BLACK);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setBackground(Color.DARK_GRAY);
        progressBar.setForeground(Color.WHITE);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(Color.DARK_GRAY);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(uploadButton);
        buttonPanel.add(copyButton);
        buttonPanel.add(saveButton);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(progressBar, BorderLayout.NORTH);

        uploadButton.addActionListener(new UploadButtonListener());
        copyButton.addActionListener(new CopyButtonListener());
        saveButton.addActionListener(new SaveButtonListener());

        frame.getContentPane().setBackground(Color.DARK_GRAY);
        frame.getContentPane().add(mainPanel);
        frame.pack();
        frame.setVisible(true);
    }

    private class UrlMouseListener extends MouseAdapter {
        private final Pattern urlPattern;

        public UrlMouseListener() {
            // Regular expression pattern to match URLs
            urlPattern = Pattern.compile("(https?://\\S+)");
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            JTextArea textArea = (JTextArea) e.getSource();
            int offset = textArea.viewToModel(e.getPoint());
            String text = textArea.getText();

            // Find URLs in the clicked area
            Matcher matcher = urlPattern.matcher(text);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (offset >= start && offset <= end) {
                    String url = matcher.group();

                    // Copy URL to clipboard
                    StringSelection selection = new StringSelection(url);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, null);

                    break; // Stop after the first URL is found
                }
            }
        }
    }

    private class UploadButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setMultiSelectionEnabled(true);
            int option = fileChooser.showOpenDialog(null);
            if (option == JFileChooser.APPROVE_OPTION) {
                File[] selectedFiles = fileChooser.getSelectedFiles();
                fileUrls = new ArrayList<>();
                progressBar.setValue(0);
                for (File file : selectedFiles) {
                    Thread uploadThread = new Thread(new Runnable() {
                        public void run() {
                            try {
                                String response = uploadFile(file);
                                JSONObject json = (JSONObject) JSONValue.parse(response);
                                boolean status = (boolean) json.get("status");
                                if (status) {
                                    JSONObject dataObject = (JSONObject) json.get("data");
                                    JSONObject fileObject = (JSONObject) dataObject.get("file");
                                    JSONObject urlObject = (JSONObject) fileObject.get("url");
                                    String fullUrl = (String) urlObject.get("full");
                                    fileUrls.add(fullUrl);
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            EventQueue.invokeLater(() -> {
                                updateTextArea();
                                progressBar.setValue(100);
                            });
                        }
                    });
                    uploadThread.start();

                }
            }
        }
    }



    private String uploadFile(File file) throws IOException {
        String boundary = Long.toHexString(System.currentTimeMillis());

        String response = RequestUtils.sendMultipartRequest(UPLOAD_URL, file, boundary, progressBar);

        return response;
    }




    private class CopyButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (fileUrls != null && !fileUrls.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                for (String url : fileUrls) {
                    builder.append(url).append(System.lineSeparator());
                }
                StringSelection selection = new StringSelection(builder.toString().trim());
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, null);
                JOptionPane.showMessageDialog(null, "URLs wurden in die Zwischenablage kopiert.");
            } else {
                JOptionPane.showMessageDialog(null, "Keine URLs zum Kopieren vorhanden.");
            }
        }
    }

    private class SaveButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (fileUrls != null && !fileUrls.isEmpty()) {
                String savePath = getSavePath();
                try (FileWriter writer = new FileWriter(savePath)) {
                    for (String url : fileUrls) {
                        writer.write(url);
                        writer.write(System.lineSeparator());
                    }
                    JOptionPane.showMessageDialog(null, "URLs wurden in der Datei gespeichert.");
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Fehler beim Speichern der Datei.");
                }
            } else {
                JOptionPane.showMessageDialog(null, "Keine URLs zum Speichern vorhanden.");
            }
        }
    }

    private void updateTextArea() {
        if (fileUrls != null && !fileUrls.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String url : fileUrls) {
                builder.append(url).append(System.lineSeparator());
            }
            textArea.setText(builder.toString().trim());
        } else {
            textArea.setText("");
        }
    }

    private String getSavePath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(DEFAULT_SAVE_PATH));
        int option = fileChooser.showSaveDialog(null);
        if (option == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            return selectedFile.getAbsolutePath();
        } else {
            return DEFAULT_SAVE_PATH;
        }
    }

    private static class RequestUtils {
        public static String sendMultipartRequest(String url, File file, String boundary, JProgressBar progressBar) throws IOException {
            String lineSeparator = System.lineSeparator();

            String request = "--" + boundary + lineSeparator +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"" + lineSeparator +
                    "Content-Type: application/octet-stream" + lineSeparator + lineSeparator;

            String endRequest = lineSeparator + "--" + boundary + "--" + lineSeparator;

            HttpURLConnection connection = null;
            OutputStream outputStream = null;
            InputStream inputStream = null;
            ByteArrayOutputStream resultStream = null;

            try {
                URL apiUrl = new URL(url);
                connection = (HttpURLConnection) apiUrl.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);

                outputStream = connection.getOutputStream();
                outputStream.write(request.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                int bufferSize = 4096; // Adjust the buffer size as needed
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                long totalBytes = file.length();
                long uploadedBytes = 0;

                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    while ((bytesRead = fileInputStream.read(buffer, 0, bufferSize)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        uploadedBytes += bytesRead;
                        int progress = (int) ((uploadedBytes * 100) / totalBytes);
                        progressBar.setValue(progress);
                        Arrays.fill(buffer, (byte) 0);
                    }
                }

                outputStream.write(endRequest.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }
                resultStream = new ByteArrayOutputStream();
                buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    resultStream.write(buffer, 0, length);
                }

                return resultStream.toString(StandardCharsets.UTF_8);
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException ignored) {
                    }
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException ignored) {
                    }
                }
                if (resultStream != null) {
                    try {
                        resultStream.close();
                    } catch (IOException ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
