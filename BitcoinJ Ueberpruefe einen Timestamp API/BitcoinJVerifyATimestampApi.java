/*
 * Herkunft/Origin: http://javacrypto.bplaced.net/
 * Programmierer/Programmer: Michael Fehr
 * Copyright/Copyright: Michael Fehr
 * Lizenttext/Licence: verschiedene Lizenzen / several licenses
 * getestet mit/tested with: Java Runtime Environment 11.0.5 x64
 * verwendete IDE/used IDE: intelliJ IDEA 2019.3.1
 * Datum/Date (dd.mm.jjjj): 22.03.2020
 * Funktion: Ueberprueft die Timestamp-Datei in der Bitcoin Blockchain mit einer Web-API
 * Function: verifies the timestamp-file in Bitcoin blockchain with a web-API
 *
 * Sicherheitshinweis/Security notice
 * Die Programmroutinen dienen nur der Darstellung und haben keinen Anspruch auf eine korrekte Funktion,
 * insbesondere mit Blick auf die Sicherheit !
 * Prüfen Sie die Sicherheit bevor das Programm in der echten Welt eingesetzt wird.
 * The program routines just show the function but please be aware of the security part -
 * check yourself before using in the real world !
 *
 * Sie benötigen diverse Bibliotheken (alle im Github-Archiv im Unterordner "libs")
 * You need a lot of libraries (see my Github-repository in subfolder "libs")
 * verwendete BitcoinJ-Bibliothek / used BitcoinJ Library: bitcoinj-core-0.15.6.jar
 * my Github-Repository: https://github.com/java-crypto/BitcoinJ
 * libs in my Github-Repo: https://github.com/java-crypto/BitcoinJ_Libraries
 *
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BitcoinJVerifyATimestampApi {

    private LocalDateTime localDateTimeStart;
    private LocalDateTime localDateTimeEnd;
    private String filenameTimestamp;
    private String filenameTimestampAppend = ".timestamp.txt";
    private byte[] sha256File = null;
    private Color tfStatusBackgroundColor;

    public BitcoinJVerifyATimestampApi() throws IOException {
        System.out.println("Das Programm arbeitet im Netzwerk: org.bitcoin.test");
        System.out.println("Guten Tag, zum Start bitte den Button 'waehlen Sie die Datei um den Zeitstempel zu verifizieren' druecken");
        tfStatusBackgroundColor = tfStatus.getBackground();
        localDateTimeStart = LocalDateTime.now();

        btnFileChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                tfFile.setText("");
                tfHash.setText("");
                tfOpReturn.setText("");
                tfProofFile.setText("");
                tfTxId.setText("");
                tfStatus.setText("");
                tfStatus.setBackground(tfStatusBackgroundColor);
                filenameTimestamp = "";
                sha256File = null;
                File file = chooseFile();
                try {
                    tfFile.setText(file.toString());
                } catch (NullPointerException e) {
                }
                if (tfFile.getText() != "") {
                    filenameTimestamp = tfFile.getText();
                    System.out.println("Datei dessen Timestamp ueberprueft werden soll: " + filenameTimestamp);
                    try {
                        sha256File = generateSha256Buffered(filenameTimestamp);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                    tfHash.setText(bytesToHex(sha256File));
                    System.out.println("\nInformationen im OP_RETURN-Bereich der Transaktion werden verifiziert:");
                    // wir pruefen ob die timestamp-datei existiert
                    String filenameTimestamp = tfFile.getText() + filenameTimestampAppend;
                    tfProofFile.setText(filenameTimestamp);
                    File fileTimestamp = new File(filenameTimestamp);
                    System.out.println("Die Timestampdatei " + filenameTimestamp + " ist vorhanden: " + fileTimestamp.exists());
                    if (!fileTimestamp.exists()) {
                        System.out.println("Die Timestampdatei ist nicht vorhanden und kann nicht verifiziert werden");
                        tfStatus.setText("Die Timestampdatei ist nicht vorhanden und kann nicht verifiziert werden");
                        tfStatus.setBackground(Color.RED);
                        return;
                    }
                    if (!fileTimestamp.canRead() || !fileTimestamp.isFile()) {
                        return;
                    }
                    BufferedReader in = null;
                    String zeile = "";
                    try {
                        in = new BufferedReader(new FileReader(fileTimestamp));
                        zeile = null;
                        // nur eine zeile wird gelesen
                        zeile = in.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (in != null)
                            try {
                                in.close();
                            } catch (IOException e) {
                            }
                    }
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println(("TxId in der Datei: " + zeile));
                    String TxId = zeile;
                    tfTxId.setText(TxId);
                    // get op_return via web api
                    String Op_Return = "";
                    try {
                        Op_Return = getOpReturnFromTransactionSmartBitShort(TxId);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.out.println("Die Transaktion beinhaltet keinen OP_RETURN");
                        tfStatus.setText("Die Transaktion beinhaltet keinen OP_RETURN");
                        tfStatus.setBackground(Color.RED);
                        return;
                    }
                    tfOpReturn.setText(Op_Return);
                    boolean verifyOpReturn = tfHash.getText().equals(tfOpReturn.getText());
                    if (verifyOpReturn == true) {
                        System.out.println("Der OP_Return stimmt ueberein, die Datei ist identisch");
                        tfStatus.setText("Der OP_Return stimmt ueberein, die Datei ist identisch");
                        tfStatus.setBackground(Color.GREEN);
                    } else {
                        System.out.println("Der OP_Return stimmt NICHT ueberein, die Datei ist NICHT identisch");
                        tfStatus.setText("Der OP Return stimmt nicht ueberein, die Datei ist nicht identisch");
                        tfStatus.setBackground(Color.RED);
                    }
                }
                // do nothing
            }
        });

        btnClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                localDateTimeEnd = LocalDateTime.now();
                tfStatus.setText("Das Programm wird beendet, bitte warten ...");
                System.out.println("Date & Time at Start: " + localDateTimeStart.toString());
                System.out.println("Date & Time at End:   " + localDateTimeEnd.toString());
                System.exit(0);
            }
        });
    }

    private File chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        } else {
            return null;
        }
    }

    public static byte[] generateSha256Buffered(String filenameString) throws IOException, NoSuchAlgorithmException {
        byte[] buffer= new byte[8192];
        int count;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filenameString));
        while ((count = bis.read(buffer)) > 0) {
            md.update(buffer, 0, count);
        }
        bis.close();
        return md.digest();
    }

    public static String getOpReturnFromTransactionSmartBitShort(String transaction) throws IOException {
        String inputLine;
        String completeLine = "";
        String OP_RETURN = "";
        URL bc_api = new URL("https://testnet-api.smartbit.com.au/v1/blockchain/tx/" + transaction + "/op-returns");
        URLConnection yc = bc_api.openConnection();
        BufferedReader in;
        try {
            in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
        } catch (FileNotFoundException e) {
            return OP_RETURN;
        }
        while ((inputLine = in.readLine()) != null) {
            completeLine = completeLine + inputLine + "\n";
        }
        //System.out.println("Ausgabe:\n" + completeLine);
        // suche nach "OP_RETURN OP_PUSHBYTES
        int fundOpReturn = completeLine.indexOf("OP_RETURN");
        if (fundOpReturn > 0) {
            //System.out.println("fundOpReturn: " + fundOpReturn);
            String OP_Return_End = "\"";
            int fundOpReturnEnd = completeLine.indexOf(OP_Return_End, fundOpReturn);
            //System.out.println("fundOpReturn End: " + fundOpReturnEnd);
            OP_RETURN = completeLine.substring(fundOpReturn, fundOpReturnEnd);
            //System.out.println("OPRETURN: " + OP_RETURN);
            // nun noch die eigentlichen werte extrahieren
            OP_RETURN = OP_RETURN.substring(10);
            //System.out.println("OPRETURN: " + OP_RETURN);
        }
        return OP_RETURN;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    private static String getActualDateReverse() {
        // provides the actual date and time in this format yyyy-MM-dd_HH-mm-ss e.g. 2020-03-16_10-27-15
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        LocalDateTime today = LocalDateTime.now();
        return formatter.format(today);
    }

    public static void main(String[] args) throws IOException {

        JFrame frame = new JFrame("Ueberpruefe einen Timestamp API");
        // umleitung der konsole in ein fenster
        RedirectedFrame outputFrameOutput = new RedirectedFrame("Output Frame", false, true, true, "BitcoinJ_VerifyTimestampApi_Output_" + getActualDateReverse() + ".txt", 700, 600, JFrame.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(new BitcoinJVerifyATimestampApi().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setSize(600, 350);
        frame.setVisible(true);
    }

    private JPanel mainPanel;
    private JButton btnFileChooser;
    private JTextField tfFile;
    private JTextField tfHash;
    private JTextField tfOpReturn;
    private JTextField tfProofFile;
    private JTextField tfTxId;
    private JTextField tfStatus;
    private JButton btnClose;
}
