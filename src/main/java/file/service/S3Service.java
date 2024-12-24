package file.service;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import file.entity.AttachmentFile;
import file.repository.AttachmentFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class S3Service {
	
	private final AmazonS3 amazonS3;
	private final AttachmentFileRepository fileRepository;
	
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;
    
    private final String DIR_NAME = "s3_data";
    
    // 파일 업로드
	@Transactional
	public void uploadS3File(@RequestPart("file") MultipartFile file) throws Exception {
		
		// C:/CE/97.data/s3_data에 파일 저장 -> S3 전송 및 저장 (putObject)
		if(file == null) {
			throw new Exception("파일 전달 오류 발생");
		}
		
		// 파일 정보 수집
		String filePath = "H://eclipse-workspace//ce//97.data//" + DIR_NAME;
		String attachmentOriginalFileName = file.getOriginalFilename();
		UUID uuid = UUID.randomUUID();
		String attachmentFileName = uuid.toString() + "_" + attachmentOriginalFileName;
		Long attachmentFileSize = file.getSize();
		
		// 빌더패턴으로 엔터티 생성
		AttachmentFile attachmentFile = AttachmentFile.builder()
				.filePath(filePath).attachmentOriginalFileName(attachmentOriginalFileName)
				.attachmentFileName(attachmentFileName).attachmentFileSize(attachmentFileSize).build();
		
		// DB에 파일 정보 저장하고 db의 ID 정보 반환받자.
		Long fileNo = fileRepository.save(attachmentFile).getAttachmentFileNo();
		
		// db에 등록이 잘 되었으면
		if (fileNo != null) {
			// 임시 파일 경로 지정
			File uploadFile = new File(attachmentFile.getFilePath() + "//" + attachmentFileName);
			// 해당 경로에 파일 저장
			file.transferTo(uploadFile);
			
			// 키는 버킷 내부의 개체가 저장되는 경로와 파일(개체)명
			String s3Key = DIR_NAME + "/" + uploadFile.getName();
			
			// s3에 해당 내용을 업로드
			amazonS3.putObject(bucketName, s3Key, uploadFile);
			
			if (uploadFile.exists()) {
				uploadFile.delete();
			}
		}
	}
	
	// 파일 다운로드
	@Transactional
	public ResponseEntity<Resource> downloadS3File(long fileNo){
		AttachmentFile attachmentFile = null;
		Resource resource = null;
		
		try {
			// DB에서 파일 검색 -> S3의 파일 가져오기 (getObject) -> 전달
			
			// DB에서 파일 검색
			attachmentFile = fileRepository.findById(fileNo).orElseThrow(() -> new NoSuchElementException("파일 없음"));
			
			// S3에서 파일 가져오기 ( 버킷 이름, DB에서 찾은 파일 경로)
			S3Object s3Object = amazonS3.getObject(bucketName, DIR_NAME + "/" + attachmentFile.getAttachmentFileName());
			
			// 가져온 파일 내용을 스트림으로 변환 
			S3ObjectInputStream s3is = s3Object.getObjectContent();
			
			// 스트림을 다운로드 가능하도록 리소스에 대입.
			resource = new InputStreamResource(s3is);
		
		} catch (Exception e) {
			return new ResponseEntity<Resource>(resource, null, HttpStatus.NO_CONTENT);
		}
		
		// 헤더에 첨부 있다고 생성
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentDisposition(ContentDisposition.builder("attachment")
				.filename(attachmentFile.getAttachmentOriginalFileName()).build());
		return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);

	}
	
}