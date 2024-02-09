package com.mrshiehx.xauth.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerialUtils/*<O extends Serializable>*/ {
    public static <O extends Serializable> O read(File file) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        O object = (O) ois.readObject();
        fis.close();
        ois.close();
        return object;
    }

    public static <O extends Serializable> void write(O object, File file) throws IOException {
        Utils.createFile(file);
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(object);
        oos.flush();
        oos.close();
    }

    public static <O extends Serializable> byte[] toByteArray(O object) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(byteArrayOutputStream);
        oos.writeObject(object);
        oos.flush();
        oos.close();
        byte[] bytes = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream.close();
        return bytes;
    }

    public static <O extends Serializable> O fromByteArray(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream fis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(fis);
        O object = (O) ois.readObject();
        fis.close();
        ois.close();
        return object;
    }
}
