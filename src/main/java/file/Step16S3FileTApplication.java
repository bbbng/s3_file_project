package file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing // 요거 없으면 db에 리스너가 없어서 생성 시간 업데이트 안됨.
public class Step16S3FileTApplication {

	public static void main(String[] args) {
		SpringApplication.run(Step16S3FileTApplication.class, args);
	}

}
