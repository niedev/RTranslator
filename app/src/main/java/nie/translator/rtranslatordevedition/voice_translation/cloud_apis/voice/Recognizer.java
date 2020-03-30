/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice;

import android.app.Service;
import android.content.Context;
import android.util.Log;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.SpeechGrpc;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.okhttp.OkHttpChannelProvider;
import io.grpc.stub.StreamObserver;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.Chronometer;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApi;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApiResult;



public class Recognizer extends CloudApi {
    public static final int CONSUMPTION_INTERVAL_IN_SECONDS = 15;
    public static final float COST_PER_INTERVAL = 0.006f;
    private static final String HOSTNAME = "speech.googleapis.com";
    private static final int PORT = 443;
    private RecognizerListener callback;
    private Chronometer chronometer = new Chronometer();
    private RecognizerApi recognizerApi = new RecognizerApi();
    private StreamObserver<StreamingRecognizeResponse> mResponseObserver;
    private StreamObserver<StreamingRecognizeRequest> mRequestObserver;
    private boolean recognizing = false;
    private ArrayDeque<ByteString> dataToRecognize = new ArrayDeque<>();
    private String currentLanguageCode;
    private final Object lock = new Object();


    public Recognizer(Service service, final boolean returnResultOnlyAtTheEnd, final RecognizerListener callback) {
        this.callback = callback;
        this.global = (Global) service.getApplication();
        this.apiTokenListener = new Global.ApiTokenListener() {
            @Override
            public void onSuccess(AccessToken apiToken) {
                recognizerApi.create(apiToken);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
            }
        };

        global.getApiToken(true, apiTokenListener);

        mResponseObserver = new StreamObserver<StreamingRecognizeResponse>() {
            private CloudApiResult ultimateInterimResult = new CloudApiResult("");
            private CloudApiResult ultimateFinalResult = new CloudApiResult("", true);

            @Override
            public void onNext(StreamingRecognizeResponse response) {
                if (response.getResultsCount() > 0) {
                    final StreamingRecognitionResult result = response.getResults(0);
                    boolean isFinal = result.getIsFinal();
                    if (result.getAlternativesCount() > 0) {
                        final SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                        float confidence = alternative.getConfidence();
                        String text = alternative.getTranscript();
                        if (text != null) {
                            if (isFinal) {
                                Log.e("recognizerResultFinal",text);
                                if (returnResultOnlyAtTheEnd) {
                                    ultimateFinalResult.setText(ultimateFinalResult.getText() + " " + text);
                                    ultimateFinalResult.setConfidenceScore(confidence);
                                } else {
                                    ultimateInterimResult = new CloudApiResult("");
                                }
                            } else {
                                Log.e("recognizerResult",text);
                                ultimateInterimResult.setText(text);
                            }
                            if (!returnResultOnlyAtTheEnd) {
                                callback.onSpeechRecognizedResult(text, currentLanguageCode, confidence, isFinal);
                            }
                        }
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                //callback.onError();
            }

            @Override
            public void onCompleted() {
                Log.e("recognizerResult","completed");
                String text;
                float confidence = 0;

                if (returnResultOnlyAtTheEnd) {
                    if (!ultimateFinalResult.getText().equals("")) {
                        text = ultimateFinalResult.getText();
                        confidence = ultimateFinalResult.getConfidenceScore();
                    } else {
                        text = ultimateInterimResult.getText();
                    }
                } else {
                    text = ultimateInterimResult.getText();
                }

                callback.onSpeechRecognizedResult(text, currentLanguageCode, confidence, true);
                ultimateInterimResult = new CloudApiResult("");
                ultimateFinalResult = new CloudApiResult("", true);
            }
        };


    }


    /**
     * Starts recognizing speech audio.
     *
     * @param sampleRate The sample rate of the audio.
     */
    public void startRecognizing(final String laguageCode, final int sampleRate, final boolean singleUtterance) {
        synchronized (lock) {
            Log.e("recognizer","startRecognitionCalled");
            if (!recognizing) {
                recognizing = true;
                if (mRequestObserver == null) {
                    currentLanguageCode = laguageCode;
                    if (recognizerApi.isReady()) {
                        performStartRecognition(currentLanguageCode, sampleRate, singleUtterance);
                    } else {
                        global.getApiToken(true, new Global.ApiTokenListener() {
                            @Override
                            public void onSuccess(AccessToken apiToken) {
                                synchronized (lock) {
                                    apiTokenListener.onSuccess(apiToken);
                                    performStartRecognition(currentLanguageCode, sampleRate, singleUtterance);
                                }
                            }

                            @Override
                            public void onFailure(int[] reasons, long value) {
                                synchronized (lock) {
                                    recognizing = false;
                                    callback.onError(reasons, value);
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    private void performStartRecognition(final String languageCode, final int sampleRate, final boolean singleUtterance) {
        //start timer
        chronometer.start();
        // Configure the API
        mRequestObserver = recognizerApi.getApi().streamingRecognize(mResponseObserver);
        mRequestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(StreamingRecognitionConfig.newBuilder()
                        .setConfig(RecognitionConfig.newBuilder()
                                .setLanguageCode(languageCode)
                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                .setSampleRateHertz(sampleRate)
                                .setEnableAutomaticPunctuation(true)
                                .setUseEnhanced(true)
                                .setEnableWordTimeOffsets(true)
                                .build())
                        // this is because single utterance is true only for the single device mode in which only the final results are taken
                        .setInterimResults(true)  //!singleUtterance
                        .setSingleUtterance(false)  //singleUtterance
                        .build())
                .build());
        Log.e("recognizer","startRecognition");
        recognize();
    }

    /**
     * Recognizes the speech audio. This method should be called every time a chunk of byte buffer
     * is ready.
     *
     * @param data The audio data.
     * @param size The number of elements that are actually relevant in the {@code data}.
     */
    public void recognize(final byte[] data, final int size) {
        synchronized (lock) {
            Log.e("recognizer","recognizingCalled");
            if (recognizing && data != null) {
                dataToRecognize.addLast(ByteString.copyFrom(data, 0, size));
                if (dataToRecognize.size() == 1) {
                    recognize();
                }
            }
        }
    }

    private void recognize() {
        if (mRequestObserver != null) {
            ByteString data = dataToRecognize.pollFirst();
            if (data != null) {
                //Call the streaming recognition API
                try {
                    mRequestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(data)
                            .build());
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                    ////e("Recognizer","call was half-closed exception");
                }
                Log.e("recognizer","recognizing");
                recognize();
            } else {     // dataToRecognize cannot contain null values ​​so if the value is null it means that dataToRecognize is empty
                if (!recognizing) {
                    performFinishRecognizing();
                }
            }
        }
    }

    /**
     * Finishes recognizing speech audio.
     */
    public void finishRecognizing() {
        synchronized (lock) {
            Log.e("recognizer","stopRecognitionCalled");
            if (recognizing) {
                recognizing = false;
                if (dataToRecognize.size() == 0) {
                    performFinishRecognizing();
                }
            }
        }
    }

    private void performFinishRecognizing() {
        if (mRequestObserver != null) {
            mRequestObserver.onCompleted();
            mRequestObserver = null;
            Log.e("recognizer","stopRecognition");
            //stop timer e sottrazione credito
            final float cost = calculateCreditConsumption(chronometer.stop(Chronometer.SECONDS));
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    global.addUsage(cost);
                }
            }.start();
        }
    }

    private float calculateCreditConsumption(float seconds) {
        int intervals = (int) Math.ceil(seconds / CONSUMPTION_INTERVAL_IN_SECONDS);
        return intervals * COST_PER_INTERVAL;
    }

    public static ArrayList<CustomLocale> getSupportedLanguages(Context context) {
        ArrayList<CustomLocale> languages = new ArrayList<>();
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(context.getResources().openRawResource(R.raw.recognizer_supported_launguages));
            NodeList list = document.getElementsByTagName("code");
            for (int i = 0; i < list.getLength(); i++) {
                languages.add(CustomLocale.getInstance(list.item(i).getTextContent()));
            }
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        return languages;
    }

    public void destroy() {
        synchronized (lock) {  // if it generates a block then we should have a thread perform the destroy operation
            // Release the gRPC channel.
            SpeechGrpc.SpeechStub mApi = recognizerApi.getApi();
            if (mApi != null) {
                final ManagedChannel channel = (ManagedChannel) mApi.getChannel();
                if (channel != null && !channel.isShutdown()) {
                    channel.shutdown();  //shutdown().awaitTermination(5, TimeUnit.SECONDS)  (it was changed because it slowed everything down if the user was talking)
                }
                recognizerApi.destroy();
            }
            mRequestObserver = null;
        }
    }

    private static class RecognizerApi {
        private SpeechGrpc.SpeechStub api;
        private AccessToken apiToken;

        public void create(AccessToken apiToken) {
            final ManagedChannel channel = new OkHttpChannelProvider()
                    .builderForAddress(HOSTNAME, PORT)
                    .nameResolverFactory(new DnsNameResolverProvider())
                    .intercept(new GoogleCredentialsInterceptor(GoogleCredentials.of(apiToken).createScoped(Global.SCOPE)))
                    .build();
            this.api = SpeechGrpc.newStub(channel);
            this.apiToken = apiToken;
        }

        public boolean isReady() {
            return api != null && apiToken.getExpirationTime().getTime() > System.currentTimeMillis();
        }

        public SpeechGrpc.SpeechStub getApi() {
            return api;
        }

        public void destroy() {
            api = null;
        }
    }

    /**
     * Authenticates the gRPC channel using the specified {@link GoogleCredentials}.
     */
    private static class GoogleCredentialsInterceptor implements ClientInterceptor {

        private final Credentials mCredentials;

        private Metadata mCached;

        private Map<String, List<String>> mLastMetadata;

        GoogleCredentialsInterceptor(Credentials credentials) {
            mCredentials = credentials;
            String string = mCredentials.getAuthenticationType();
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(final MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, final Channel next) {
            return new ClientInterceptors.CheckedForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
                @Override
                protected void checkedStart(Listener<RespT> responseListener, Metadata headers) throws StatusException {
                    Metadata cachedSaved;
                    URI uri = serviceUri(next, method);
                    synchronized (this) {
                        Map<String, List<String>> latestMetadata = getRequestMetadata(uri);
                        if (mLastMetadata == null || mLastMetadata != latestMetadata) {
                            mLastMetadata = latestMetadata;
                            mCached = toHeaders(mLastMetadata);
                        }
                        cachedSaved = mCached;
                    }
                    headers.merge(cachedSaved);
                    delegate().start(responseListener, headers);
                }
            };
        }

        /**
         * Generate a JWT-specific service URI. The URI is simply an identifier with enough
         * information for a service to know that the JWT was intended for it. The URI will
         * commonly be verified with a simple string equality check.
         */
        private URI serviceUri(Channel channel, MethodDescriptor<?, ?> method) throws StatusException {
            String authority = channel.authority();
            if (authority == null) {
                throw Status.UNAUTHENTICATED
                        .withDescription("Channel has no authority")
                        .asException();
            }
            // Always use HTTPS, by definition.
            final String scheme = "https";
            final int defaultPort = 443;
            String path = "/" + MethodDescriptor.extractFullServiceName(method.getFullMethodName());
            URI uri;
            try {
                uri = new URI(scheme, authority, path, null, null);
            } catch (URISyntaxException e) {
                throw Status.UNAUTHENTICATED
                        .withDescription("Unable to construct service URI for auth")
                        .withCause(e).asException();
            }
            // The default port must not be present. Alternative ports should be present.
            if (uri.getPort() == defaultPort) {
                uri = removePort(uri);
            }
            return uri;
        }

        private URI removePort(URI uri) throws StatusException {
            try {
                return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), -1 /* port */,
                        uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException e) {
                throw Status.UNAUTHENTICATED
                        .withDescription("Unable to construct service URI after removing port")
                        .withCause(e).asException();
            }
        }

        private Map<String, List<String>> getRequestMetadata(URI uri) throws StatusException {
            try {
                return mCredentials.getRequestMetadata(uri);
            } catch (IOException e) {
                throw Status.UNAUTHENTICATED.withCause(e).asException();
            }
        }

        private static Metadata toHeaders(Map<String, List<String>> metadata) {
            Metadata headers = new Metadata();
            if (metadata != null) {
                for (String key : metadata.keySet()) {
                    Metadata.Key<String> headerKey = Metadata.Key.of(
                            key, Metadata.ASCII_STRING_MARSHALLER);
                    for (String value : metadata.get(key)) {
                        headers.put(headerKey, value);
                    }
                }
            }
            return headers;
        }
    }
}
