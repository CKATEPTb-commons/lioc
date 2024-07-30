package dev.ckateptb.container.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class PackageScanner {
    @SneakyThrows
    public static List<Class<?>> getClassesInPackage(String packageName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        URL packageURL = classLoader.getResource(path);
        if (packageURL == null) throw new ClassNotFoundException("Package not found: " + packageName);
        File directory = new File(URLDecoder.decode(packageURL.getFile(), StandardCharsets.UTF_8));
        List<Class<?>> classes = new ArrayList<>();
        if (directory.exists()) findClasses(directory, packageName, classes);
        return classes;
    }

    private static void findClasses(File directory, String packageName, List<Class<?>> classes) throws ClassNotFoundException {
        if (!directory.exists()) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                findClasses(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                classes.add(Class.forName(className));
            }
        }
    }
}
