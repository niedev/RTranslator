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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**Not used**/
public class FileManager {
    private File directory;

    public FileManager(File directory){
        this.directory= directory;
    }

    public Object getObjectFromFile(String fileName, Object defValue){
        File file = new File(directory, fileName);
        Object object=defValue;

        if(file.exists() && file.canRead()){
            try {
                ObjectInputStream objectInputStream= new ObjectInputStream(new FileInputStream(file));
                object= objectInputStream.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return object;
    }

    public void setObjectInFile(String fileName,Object object){
        File file = new File(directory, fileName);

        try {
            ObjectOutputStream objectOutputStream= new ObjectOutputStream(new FileOutputStream(file));
            objectOutputStream.writeObject(object);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
