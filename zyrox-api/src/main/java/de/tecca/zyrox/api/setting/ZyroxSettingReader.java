package de.tecca.zyrox.api.setting;

@FunctionalInterface
public interface ZyroxSettingReader {

    Object read() throws Exception;
}
