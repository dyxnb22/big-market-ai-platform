package com.dyx.market.market;

import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * NR-003: DAO 接口方法必须在 market-service Mapper XML 中有对应 statement。
 */
public class MarketDaoMapperStatementTest {

  private static final Pattern ID_PATTERN = Pattern.compile("\\bid=\"([^\"]+)\"");

  @Test
  public void raffleActivityAccountDaoStatementsPresent() throws Exception {
    String xml = loadMapperText("mybatis/mapper/mysql/raffle_activity_account_mapper.xml");
    for (Method method : com.dyx.market.infrastructure.dao.IRaffleActivityAccountDao.class.getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      }
      assertTrue("missing statement for " + method.getName(),
          xml.contains("id=\"" + method.getName() + "\""));
    }
  }

  @Test
  public void userAwardRecordDaoStatementsPresent() throws Exception {
    String xml = loadMapperText("mybatis/mapper/mysql/user_award_record_mapper.xml");
    for (Method method : com.dyx.market.infrastructure.dao.IUserAwardRecordDao.class.getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      }
      assertTrue("missing statement for " + method.getName(),
          xml.contains("id=\"" + method.getName() + "\""));
    }
  }

  @Test
  public void userCreditOrderDaoStatementsPresent() throws Exception {
    String xml = loadMapperText("mybatis/mapper/mysql/user_credit_order_mapper.xml");
    for (Method method : com.dyx.market.infrastructure.dao.IUserCreditOrderDao.class.getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      }
      assertTrue("missing statement for " + method.getName(),
          xml.contains("id=\"" + method.getName() + "\""));
    }
  }

  private static String loadMapperText(String classpathResource) throws Exception {
    try (InputStream in = MarketDaoMapperStatementTest.class.getClassLoader().getResourceAsStream(classpathResource)) {
      if (in == null) {
        fail("mapper not found: " + classpathResource);
      }
      Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
      return scanner.hasNext() ? scanner.next() : "";
    }
  }
}
