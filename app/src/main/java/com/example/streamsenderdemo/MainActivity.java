package com.example.streamsenderdemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.example.streamsenderdemo.ui.main.SectionsPagerAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.common.primitives.UnsignedLong;

import org.interledger.codecs.ilp.InterledgerCodecContextFactory;
import org.interledger.core.InterledgerAddress;
import org.interledger.core.SharedSecret;
import org.interledger.link.Link;
import org.interledger.link.http.IlpOverHttpLink;
import org.interledger.link.http.IlpOverHttpLinkSettings;
import org.interledger.link.http.IncomingLinkSettings;
import org.interledger.link.http.OutgoingLinkSettings;
import org.interledger.link.http.auth.SimpleBearerTokenSupplier;
import org.interledger.spsp.PaymentPointer;
import org.interledger.spsp.StreamConnectionDetails;
import org.interledger.spsp.client.rust.InterledgerRustNodeClient;
import org.interledger.stream.Denominations;
import org.interledger.stream.SendMoneyRequest;
import org.interledger.stream.SendMoneyResult;
import org.interledger.stream.SenderAmountMode;
import org.interledger.stream.sender.FixedSenderAmountPaymentTracker;
import org.interledger.stream.sender.SimpleStreamSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class MainActivity extends AppCompatActivity {

    private static final String TESTNET_URI = "https://rs3.xpring.dev";
    // NOTE - replace this with your own sender account info (see https://xpring.io/ilp-testnet-creds)
    private static final String SENDER_ACCOUNT_USERNAME = "user_nqtysao8";
    private static final String SENDER_ACCOUNT_PASS_KEY = "kppk47zr9027c";
    public static final String SENDER_PAYMENT_POINTER =
            "$rs3.xpring.dev/accounts/" + SENDER_ACCOUNT_USERNAME + "/spsp";

    // NOTE - replace this with your own sender account info (see https://xpring.io/ilp-testnet-creds)
    private static final String RECEIVER_ACCOUNT_USERNAME = "user_ea155yv8";
    private static final String RECEIVER_ACCOUNT_PASS_KEY = "p05rj7sksjgcp";
    public static final String RECEIVER_PAYMENT_POINTER =
            "$rs3.xpring.dev/accounts/" + RECEIVER_ACCOUNT_USERNAME + "/spsp";
    private static final InterledgerAddress SENDER_ILP_ADDRESS =
            InterledgerAddress.of("test.xpring-dev.rs3").with(SENDER_ACCOUNT_USERNAME);
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static OkHttpClient newHttpClient() {
        ConnectionPool connectionPool = new ConnectionPool(10, 5, TimeUnit.MINUTES);
        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).build();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(spec, ConnectionSpec.CLEARTEXT))
                .cookieJar(CookieJar.NO_COOKIES)
                .connectTimeout(5000, TimeUnit.MILLISECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);
        return builder.connectionPool(connectionPool).build();
    }

    private static Link newIlpOverHttpLink() {
        String sharedSecret = "some random secret here";
        final IlpOverHttpLinkSettings linkSettings = IlpOverHttpLinkSettings.builder()
                .incomingHttpLinkSettings(IncomingLinkSettings.builder()
                        .authType(IlpOverHttpLinkSettings.AuthType.SIMPLE)
                        .encryptedTokenSharedSecret(sharedSecret)
                        .build())
                .outgoingHttpLinkSettings(OutgoingLinkSettings.builder()
                        .authType(IlpOverHttpLinkSettings.AuthType.SIMPLE)
                        .tokenSubject(SENDER_ACCOUNT_USERNAME)
                        .url(HttpUrl.parse(TESTNET_URI + "/ilp"))
                        .encryptedTokenSharedSecret(sharedSecret)
                        .build())
                .build();

        return new IlpOverHttpLink(
                () -> SENDER_ILP_ADDRESS,
                linkSettings,
                newHttpClient(),
                new ObjectMapper(),
                InterledgerCodecContextFactory.oer(),
                new SimpleBearerTokenSupplier(SENDER_ACCOUNT_USERNAME + ":" + SENDER_ACCOUNT_PASS_KEY)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        final ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);
        FloatingActionButton fab = findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Streaming Money to " + RECEIVER_PAYMENT_POINTER, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                try {
                    BigDecimal senderBalance = new GetBalanceAsyncTask().execute(AuthInfo.of(SENDER_ACCOUNT_USERNAME, SENDER_ACCOUNT_PASS_KEY)).get();
                    logger.info("Sender Balance (before): {}", senderBalance);
                    BigDecimal receiverBalance = new GetBalanceAsyncTask().execute(AuthInfo.of(RECEIVER_ACCOUNT_USERNAME, RECEIVER_ACCOUNT_PASS_KEY)).get();
                    logger.info("Receiver Balance (before): {}", receiverBalance);

                    SendMoneyResult sendMoneyResult = new SendMoneyAsyncTask().execute(UnsignedLong.valueOf(5000L)).get();
                    logger.info("Send money result: {}", sendMoneyResult);

                    senderBalance = new GetBalanceAsyncTask().execute(AuthInfo.of(SENDER_ACCOUNT_USERNAME, SENDER_ACCOUNT_PASS_KEY)).get();
                    logger.info("Sender Balance (after): {}", senderBalance);
                    receiverBalance = new GetBalanceAsyncTask().execute(AuthInfo.of(RECEIVER_ACCOUNT_USERNAME, RECEIVER_ACCOUNT_PASS_KEY)).get();
                    logger.info("Receiver Balance (after): {}", receiverBalance);
                } catch (ExecutionException | InterruptedException e) {
                    logger.error(e.getMessage(), e);
                }

            }
        });
    }

    class SendMoneyAsyncTask extends AsyncTask<UnsignedLong, Void, SendMoneyResult> {

        private Exception exception;

        @Override
        protected SendMoneyResult doInBackground(UnsignedLong... amountsToSend) {

            try {
                UnsignedLong amountToSend = amountsToSend[0];

                // Fetch shared secret and destination address using SPSP client
                InterledgerRustNodeClient spspClient = new InterledgerRustNodeClient(newHttpClient(),
                        SENDER_ACCOUNT_USERNAME + ":" + SENDER_ACCOUNT_PASS_KEY, TESTNET_URI);
                StreamConnectionDetails connectionDetails = spspClient
                        .getStreamConnectionDetails(PaymentPointer.of(RECEIVER_PAYMENT_POINTER));

                SimpleStreamSender simpleStreamSender = new SimpleStreamSender(newIlpOverHttpLink());

                // Send payment using STREAM
                SendMoneyResult sendMoneyResult = null;
                try {
                    sendMoneyResult = simpleStreamSender.sendMoney(
                            SendMoneyRequest.builder()
                                    .sourceAddress(SENDER_ILP_ADDRESS)
                                    .senderAmountMode(SenderAmountMode.SENDER_AMOUNT)
                                    .amount(amountToSend)
                                    .denomination(Denominations.XRP)
                                    .destinationAddress(connectionDetails.destinationAddress())
                                    .timeout(Duration.ofMillis(30000))
                                    .paymentTracker(new FixedSenderAmountPaymentTracker(amountToSend))
                                    .sharedSecret(SharedSecret.of(connectionDetails.sharedSecret().value()))
                                    .build()
                    ).get();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
                return sendMoneyResult;
            } catch (Exception e) {
                this.exception = e;
                return null;
            }
        }

        protected void onPostExecute(SendMoneyResult sendMoneyResult) {
            // logger.info("Send money result: {}", sendMoneyResult);
            // TODO: Update TEXT Field display...
        }

    }


    class GetBalanceAsyncTask extends AsyncTask<AuthInfo, Void, BigDecimal> {

        private Exception exception;

        @Override
        protected BigDecimal doInBackground(AuthInfo... authInfos) {

            AuthInfo authInfo = authInfos[0];
            try {
                // Fetch shared secret and destination address using SPSP client
                InterledgerRustNodeClient spspClient = new InterledgerRustNodeClient(newHttpClient(),
                        authInfo.getAccountUserName() + ":" + authInfo.getAccountPassKey(), TESTNET_URI);
                return spspClient.getBalance(authInfo.getAccountUserName());
            } catch (Exception e) {
                this.exception = e;
                return null;
            }
        }
    }
}