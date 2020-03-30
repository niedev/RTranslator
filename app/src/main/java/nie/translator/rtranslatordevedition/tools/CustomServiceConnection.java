/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslatordevedition.tools;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCallback;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCommunicator;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCommunicatorListener;

public class CustomServiceConnection implements ServiceConnection {
    private ServiceCommunicator serviceCommunicator;
    private ArrayList<ServiceCallback> callbacksToAddOnBind = new ArrayList<>();
    private ArrayList<ServiceCommunicatorListener> callbacksToRespondOnBind = new ArrayList<>();

    public CustomServiceConnection(ServiceCommunicator serviceCommunicator){
        this.serviceCommunicator=serviceCommunicator;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder iBinder) {
        serviceCommunicator.initializeCommunication(new Messenger(iBinder));
        for(int i = 0; i< callbacksToAddOnBind.size(); i++) {
            serviceCommunicator.addCallback(callbacksToAddOnBind.get(i));
        }
        for(int i = 0; i< callbacksToRespondOnBind.size(); i++) {
            ServiceCommunicatorListener responseListener1= callbacksToRespondOnBind.get(i);
            if(responseListener1!=null) {
                responseListener1.onServiceCommunicator(serviceCommunicator);
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {}

    public void onServiceDisconnected(){
        for(int i=0;i<callbacksToAddOnBind.size();i++) {
            serviceCommunicator.removeCallback(callbacksToAddOnBind.get(i));
        }
        serviceCommunicator.stopCommunication();
    }

    public ServiceCommunicator getServiceCommunicator() {
        return serviceCommunicator;
    }

    public void addCallbacks(ServiceCallback serviceCallback, ServiceCommunicatorListener responseListener){
        callbacksToAddOnBind.add(serviceCallback);
        callbacksToRespondOnBind.add(responseListener);
    }
}
