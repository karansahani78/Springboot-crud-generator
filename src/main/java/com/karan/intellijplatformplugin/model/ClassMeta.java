package com.karan.intellijplatformplugin.model;

import java.util.List;

public class ClassMeta {

    private final String className;
    private final String packageName;
    private final String idType;
    private final List<FieldMeta> fields;

    public ClassMeta(String className, String packageName,
                     String idType, List<FieldMeta> fields) {
        this.className = className;
        this.packageName = packageName;
        this.idType = idType;
        this.fields = fields;
    }

    public String getClassName() { return className; }
    public String getPackageName() { return packageName; }
    public String getIdType() { return idType; }
    public List<FieldMeta> getFields() { return fields; }

    public String basePackage() {
        if (packageName.endsWith(".model") || packageName.endsWith(".entity")) {
            return packageName.substring(0, packageName.lastIndexOf('.'));
        }
        return packageName;
    }
}
