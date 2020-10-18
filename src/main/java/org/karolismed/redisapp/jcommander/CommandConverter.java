package org.karolismed.redisapp.jcommander;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import org.karolismed.redisapp.constants.Command;

import java.util.Optional;

public class CommandConverter implements IStringConverter<Command> {
    @Override
    public Command convert(String value) {
        return Optional.ofNullable(Command.fromString(value))
            .orElseThrow(() -> new ParameterException(
                "Invalid value " + value + " for command"
            ));
    }
}