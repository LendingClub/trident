package org.lendingclub.trident;

import org.junit.runner.RunWith;
import org.lendingclub.trident.config.TridentConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ComponentScan(basePackageClasses = TridentConfig.class)
@SpringBootTest
public abstract class TridentIntegrationTest {


}
