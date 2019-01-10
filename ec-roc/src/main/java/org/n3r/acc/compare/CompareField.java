package org.n3r.acc.compare;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.lang.RStr;

public class CompareField {
    private FieldReference left;
    private FieldReference right;

    public void createCompareKey(String leftKey, String rightKey) {
        if (StringUtils.isEmpty(leftKey))
            throw new RuntimeException("leftKey is invalid");
        if (StringUtils.isEmpty(rightKey))
            throw new RuntimeException("rightKey is invalid");

        left = new FieldReference(leftKey);
        right = new FieldReference(rightKey);
    }

    public FieldReference getRight() {
        return right;
    }

    public FieldReference getLeft() {
        return left;
    }

    @Override
    public String toString() {
        return "CompareField{" +
                "left=" + left +
                ", right=" + right +
                '}';
    }
}
