package io.geekshop.resolver.helloworld;

import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import io.geekshop.custom.security.Allow;
import lombok.Data;
import org.springframework.stereotype.Component;


/**
 * @see https://www.cnblogs.com/coderxiaohei/p/14204840.html
 * 在客户端中打开演示提交,返回数据即可


  http://127.0.0.1:8080/playground

  query{
    getBookById(id:1){
      id,name
    }
  }

 */
@Component
public class BookTestQuery implements GraphQLQueryResolver {

    @Allow
    public Book getBookById(int id,DataFetchingEnvironment dfe) {
        Book book = new Book();
        book.setId(id);
        book.setName("这边书没有书名");
        return book;
    }
}

@Data
class Book {
    private int id;
    private String name;
}
