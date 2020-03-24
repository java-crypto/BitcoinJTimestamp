/*
 * Herkunft/Origin: http://javacrypto.bplaced.net/
 * Programmierer/Programmer: Michael Fehr
 * Copyright/Copyright: Michael Fehr
 * Lizenttext/Licence: verschiedene Lizenzen / several licenses
 * getestet mit/tested with: Java Runtime Environment 11.0.5 x64
 * verwendete IDE/used IDE: intelliJ IDEA 2019.3.1
 * Datum/Date (dd.mm.jjjj): 24.03.2020
 * Funktion: Erzeugt eine OP_RETURN-Transaktion in der Bitcoin Blockchain
 * Function: create an OP_RETURN transaction in the Bitcoin blockchain
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

import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class BitcoinJCreateAnOp_Return {
    private WalletAppKit kit;
    boolean kitIsRunning = false;
    private NetworkParameters netParams;
    private LocalDateTime localDateTimeStart;
    private LocalDateTime localDateTimeEnd;
    private String filenameWallet = "TimestampOwn";
    private Coin balance;
    private Sha256Hash sha256Hash;
    private Color colorStatus;
    private String labelLinkPreset = "noch kein Link vorhanden";

    public BitcoinJCreateAnOp_Return() throws IOException, InterruptedException {
        // choose network type (MAIN, TEST or REG)
        //String networkType = "MAIN";
        String networkType = "TEST";
        //String networkType = "REG";
        switch (networkType) {
            case "MAIN": {
                netParams = MainNetParams.get();
                filenameWallet = filenameWallet + "_Mainnet";
                break;
            }
            case "TEST": {
                netParams = TestNet3Params.get();
                filenameWallet = filenameWallet + "_Testnet";
                break;
            }
            case "REG": {
                netParams = RegTestParams.get();
                filenameWallet = filenameWallet + "_Regtest";
                break;
            }
            default: {
                System.out.println("Es ist kein networkType angegeben, das Programm wird in 10 Sekunden beendet");
                tfStatus.setText("Kein networkType angegeben, Programm endet in 10 Sekunden");
                tfStatus.setBackground(Color.RED);
                TimeUnit.SECONDS.sleep(10);
                System.exit(0);
            }
        }
        System.out.println("Das Programm arbeitet im Netzwerk: " + netParams.getId());
        System.out.println("Guten Tag, zum Start bitte den Button 'starte das wallet' druecken");
        localDateTimeStart = LocalDateTime.now();
        kitIsRunning = false;
        colorStatus = tfStatus.getBackground();
        lblLink.setText(labelLinkPreset);

        btnSendOpReturn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                tfStatus.setText("");
                tfTxId.setText("");
                lblLink.setText(labelLinkPreset);
                lblLink.setToolTipText("");
                lblLink.setCursor(Cursor.getDefaultCursor());
                tfStatus.setBackground(colorStatus);
                sha256Hash = null;
                // is an op_return string provided?
                String txOpReturn = tfOpReturn.getText();
                if (txOpReturn.length() == 0) {
                    System.out.println("Bitte einen OP_RETURN-Wert eingeben");
                    tfStatus.setText("Kein OP_RETURN angegeben");
                    tfStatus.setBackground(Color.YELLOW);
                }

                byte[] txOpReturnByte = new byte[0];
                try {
                    txOpReturnByte = txOpReturn.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (cbxExtendedOpReturn.isSelected()) {
                    System.out.println("OP_RETURN Laenge: " + txOpReturn.length() + " (maximal 80 Zeichen) : " + txOpReturn);
                } else {
                    System.out.println("OP_RETURN Laenge: " + txOpReturn.length() + " (maximal 40 Zeichen) : " + txOpReturn);
                }
                System.out.println("OP_Return als ByteArray: " + bytesToHex(txOpReturnByte));
                System.out.println("\nErzeugung einer Transaktion und des SendRequests");
                // construct an OP_RETURN transaction
                Transaction transaction = new Transaction(netParams);
                transaction.addOutput(
                        Coin.ZERO,
                        ScriptBuilder.createOpReturnScript(txOpReturnByte)
                );
                // transaction is used to create a sendrequest
                SendRequest sendRequest = SendRequest.forTx(transaction);
                System.out.println("Transaktion           : " + transaction);
                Coin coinsFee = Coin.valueOf(1000); // reduced fee may not work in MainNet
                System.out.println("Standard Fee per KB   : " + sendRequest.feePerKb.toFriendlyString());
                System.out.println("Reduzierte Fee per KB : " + coinsFee.toFriendlyString());
                sendRequest.feePerKb = coinsFee;
                System.out.println("SendRequest           : " + sendRequest);
                System.out.println("\nSende den SendRequest ueber das Wallet und erhalte ein SendResult");
                Wallet.SendResult sendResult = null;
                try {
                    sendResult = kit.wallet().sendCoins(sendRequest);
                } catch (InsufficientMoneyException e) {
                    System.out.println("Fehler: ungenuegendes Guthaben - laden Sie Bitcons in das Wallet");
                    tfStatus.setText("Fehler: ungenuegendes Guthaben - laden Sie Bitcons in das Wallet");
                    tfStatus.setBackground(Color.RED);
                    System.out.println("Aktuelle Empfangsadresse: " + kit.wallet().currentReceiveAddress());
                    btnSendOpReturn.setEnabled(false);
                    e.printStackTrace();
                }
                System.out.println("SendResult  : " + sendResult.tx);
                System.out.println("Transaktions Id          : " + sendResult.tx.getTxId().toString());
                tfTxId.setText(sendResult.tx.getTxId().toString());
                lblLink.setText("<HTML><FONT color=\"#000099\"><u>"
                        + "https://tbtc.bitaps.com/" + tfTxId.getText() + "</u></FONT></HTML>");
                if (!tfTxId.getText().equals(""))
                    lblLink.setToolTipText("https://tbtc.bitaps.com/" + tfTxId.getText());
                lblLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
                System.out.println("Direktlink zur Onlineanzeige: " + "https://tbtc.bitaps.com/" + tfTxId.getText());
                System.out.println("Das aktuell verfuegbare Guthaben ist: " + kit.wallet().getBalance().toFriendlyString() + (" = ") + kit.wallet().getBalance() + " Satoshi");
                tfBalance.setText(kit.wallet().getBalance().toFriendlyString() + (" = ") + kit.wallet().getBalance() + " Satoshi");
                System.out.println("Aktuelle Empfangsadresse : " + kit.wallet().currentReceiveAddress());
                tfStatus.setText("OP_RETURN gespeichert");
                tfStatus.setBackground(Color.GREEN);
            }
            // do nothing
        });

        btnClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                btnSendOpReturn.setEnabled(false);
                btnClose.setEnabled(false);
                btnStartKit.setEnabled(false);
                progressBarWait.setString("Das Programm wird beendet, bitte warten ...");
                tfStatus.setText("Das Programm wird beendet, bitte warten ...");
                progressBarWait.setStringPainted(true);
                progressBarWait.setVisible(true);
                progressBarWait.setIndeterminate(true);
                Thread thread = new Thread() {
                    public void run() {
                        System.out.println("btnClose laeuft im separaten Thread");
                        // stop kit
                        try {
                            Thread.sleep(1000); // 5 seconds to end
                        } catch (InterruptedException e) {
                        }
                        if (kitIsRunning) {
                            kit.stopAsync();
                            kit.awaitTerminated();
                        }
                        localDateTimeEnd = LocalDateTime.now();
                        System.out.println("Das Programm endet jetzt");
                        System.out.println("Datum & Zeit am Start: " + localDateTimeStart.toString());
                        System.out.println("Datum & Zeit am Ende : " + localDateTimeEnd.toString());
                        System.exit(0);
                    }
                };
                thread.start();
            }
        });

        btnStartKit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                // disable start button to prevent a second start
                btnStartKit.setEnabled(false);
                progressBarWait.setString("Das Wallet wird aktualisiert, bitte warten ...");
                progressBarWait.setStringPainted(true);
                progressBarWait.setVisible(true);
                progressBarWait.setIndeterminate(true);
                if (kitIsRunning) {
                    System.out.println("Das Wallet ist bereits gestartet");
                    return;
                }
                // actual date and time
                localDateTimeStart = LocalDateTime.now();
                System.out.println("Lade ein HD-Wallet mit BitcoinJ im Bitcoin Testnet und erzeuge eine OP_RETURN Transaktion mit reduzierten Kosten\n");
                System.out.println("Das Programm benutzt das BitcoinJ WalletAppKit\n");
                System.out.println("Bitte benutzen Sie Java 11 und bitcoinj-core-0.15.6.jar fuer dieses Programm\n");
                Thread thread = new Thread() {
                    public void run() {
                        System.out.println("btnStartKit laeuft im separaten Thread");
                        kit = new WalletAppKit(netParams, new File("."), filenameWallet);
                        kit.setAutoSave(true);
                        if (netParams == RegTestParams.get()) {
                            kit.connectToLocalHost(); // für regtest notwendig
                        }
                        System.out.println("Das Wallet wurde geladen: " + filenameWallet);
                        System.out.println("\nDas Wallet aktualisiert die Blockchain in der Datei " + filenameWallet + ".spvchain");
                        System.out.println("Bitte haben Sie eine bis drei Minuten Geduld");
                        kit.startAsync();
                        kit.awaitRunning();
                        while (!kit.isRunning()) {
                            try {
                                wait(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        // kit is running
                        progressBarWait.setVisible(false);
                        progressBarWait.setIndeterminate(false);
                        kit.wallet().allowSpendingUnconfirmedTransactions(); // nur eigene gelder, daher erlaube ich das mal
                        System.out.println("Das aktuell verfuegbare Guthaben ist: " + kit.wallet().getBalance().toFriendlyString() + (" = ") + kit.wallet().getBalance() + " Satoshi");
                        tfBalance.setText(kit.wallet().getBalance().toFriendlyString() + (" = ") + kit.wallet().getBalance() + " Satoshi");
                        balance = kit.wallet().getBalance();
                        System.out.println("Aktuelle Empfangsadresse            : " + kit.wallet().currentReceiveAddress());
                        System.out.println("Das Programm ist betriebsbereit und wartet auf den Einsatz");
                        kitIsRunning = true;
                        tfStatus.setText("Das WalletAppKit laeuft");
                        btnSendOpReturn.setEnabled(true);
                    }
                };
                thread.start();
            }
        });

        tfOpReturn.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                super.keyTyped(e);
                tfOpReturnRestrictLength();
            }
        });

        cbxExtendedOpReturn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                tfOpReturnRestrictLength();
            }
        });

        lblLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                String labelLinkText = lblLink.getText();
                if (!labelLinkText.equals(labelLinkPreset)) {
                    try {
                        Desktop.getDesktop().browse(new URI("https://tbtc.bitaps.com/" + tfTxId.getText()));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } catch (URISyntaxException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
    }

    private void tfOpReturnRestrictLength() {
        // check if tfOpReturn has more characters than allowed
        // 1234567890123456789012345678901234567890
        String str = tfOpReturn.getText();
        int strLengthAllowed = 40;
        if (cbxExtendedOpReturn.isSelected() == true) {
            strLengthAllowed = 80;
        }
        if (str.length() > strLengthAllowed) {
            str = str.substring(0, strLengthAllowed);
        }
        tfOpReturn.setText(str);
    }

    private String bytesToHex(byte[] bytes) {
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

    public static void main(String[] args) throws IOException, InterruptedException {
        JFrame frame = new JFrame("Erzeuge eine OP_RETURN-Transaktion in der Bitcoin Blockchain");
        RedirectedFrame outputFrameErrors = new RedirectedFrame("Log Frame", true, false, true, "BitcoinJ_CreateOpReturn_Logfile_" + getActualDateReverse() + ".txt", 750, 650, JFrame.DO_NOTHING_ON_CLOSE);
        RedirectedFrame outputFrameOutput = new RedirectedFrame("Output Frame", false, true, true, "BitcoinJ_CreateOpReturn_Output_" + getActualDateReverse() + ".txt", 700, 600, JFrame.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(new BitcoinJCreateAnOp_Return().mainPanel);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setSize(730, 380);
        frame.setVisible(true);
    }

    private JPanel mainPanel;
    private JButton btnSendOpReturn;
    private JTextField tfBalance;
    private JTextField tfTxId;
    private JTextField tfStatus;
    private JButton btnClose;
    private JButton btnStartKit;
    private JProgressBar progressBarWait;
    private JTextField tfOpReturn;
    private JCheckBox cbxExtendedOpReturn;
    private JLabel lblLink;
}