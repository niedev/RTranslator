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

package nie.translator.rtranslatordevedition.tools.services_communication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Messenger;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class ServiceCommunicator {
    protected Handler serviceHandler;
    protected Messenger serviceMessenger;
    private int id;

    protected ServiceCommunicator(@NonNull int id1) {
        this.id = id1;
    }

    public void initializeCommunication(Messenger serviceMessenger) {
        this.serviceMessenger = serviceMessenger;
    }

    public void stopCommunication() {
        this.serviceMessenger = null;
    }

    protected void sendToService(Bundle bundle) {
        if (serviceMessenger != null) {
            android.os.Message message = android.os.Message.obtain();
            message.setData(bundle);
            try {
                serviceMessenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public Messenger getServiceMessenger() {
        return serviceMessenger;
    }

    public boolean isCommunicating() {
        return serviceMessenger != null;
    }

    public abstract void addCallback(ServiceCallback serviceCallback);

    public abstract int removeCallback(ServiceCallback serviceCallback);

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof ServiceCommunicator){
            ServiceCommunicator serviceCommunicator= (ServiceCommunicator) obj;
            return serviceCommunicator.getId() == id;
        }else{
            return false;
        }
    }
}
