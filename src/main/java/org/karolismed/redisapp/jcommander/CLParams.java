package org.karolismed.redisapp.jcommander;

import com.beust.jcommander.Parameter;
import lombok.Getter;
import org.karolismed.redisapp.constants.Command;

@Getter
public class CLParams {

    @Parameter(converter = CommandConverter.class, required = true)
    private Command command;

    @Parameter(names = {"-username", "-u"})
    private String userName;

    @Parameter(names = {"-password", "-pw"})
    private String password;

    @Parameter(names = {"-title", "-t"})
    private String title;

    @Parameter(names = {"-author", "-a"})
    private String author;

    @Parameter(names = {"-city", "-c"})
    private String city;

    @Parameter(names = {"-lib_id", "-lid"})
    private Integer libraryId;

    @Parameter(names = {"-book_id", "-bid"})
    private Integer bookId;
}
