package io.github.eschoe.llmragapi.util;

import org.springframework.stereotype.Component;

@Component
public class PgVectorUtil {

    public String toPgvectorLiteral(float[] v) {

        StringBuilder sb = new StringBuilder("(");

        for (int i = 0; i < v.length; i++) {
            if (i > 0) {sb.append(",");}
            sb.append(v[i]);
        }

        sb.append(")");

        return sb.toString();

    }

}
