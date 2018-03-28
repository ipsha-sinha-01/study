package com.springBootBasics.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class RestBook {

   @GetMapping("/book")
  public List<Book> returnBook(){
       return Arrays.asList(new Book(1,"wakanda"));

   }


}
