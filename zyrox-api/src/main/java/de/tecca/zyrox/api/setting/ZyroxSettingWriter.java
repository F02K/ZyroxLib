package de.tecca.zyrox.api.setting;

@FunctionalInterface
public interface ZyroxSettingWriter {

    ZyroxSettingResult write(String value) throws Exception;
}
