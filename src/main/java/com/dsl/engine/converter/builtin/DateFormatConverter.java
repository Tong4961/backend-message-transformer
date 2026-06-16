package com.dsl.engine.converter.builtin;

import com.dsl.engine.converter.FunctionConverter;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class DateFormatConverter implements FunctionConverter {
    @Override
    public String getName() { return "dateformat"; }

    @Override
    public Object convert(Object input, String... params) {
        if (input == null) return null;
        String sourceFormat = params.length > 0 ? params[0] : "yyyy-MM-dd";
        String targetFormat = params.length > 1 ? params[1] : "yyyy-MM-dd HH:mm:ss";
        try {
            SimpleDateFormat sdfIn = new SimpleDateFormat(sourceFormat);
            SimpleDateFormat sdfOut = new SimpleDateFormat(targetFormat);
            Date date = sdfIn.parse(String.valueOf(input));
            return sdfOut.format(date);
        } catch (Exception e) {
            return String.valueOf(input);
        }
    }
}
