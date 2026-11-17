# Java 코드 스타일

- Java 소스와 테스트에 google-java-format 1.24.0의 AOSP 스타일을 적용함.
- 들여쓰기는 공백 4칸이며, 메서드·애너테이션·인자 목록과 import 정리는 포맷터 기준을 따름.
- 이름 변경, 메서드 분리, 조건 변경은 스타일 변경과 별도 작업으로 관리함.
- SQL·Flyway·HTML·CSS·JavaScript는 이번 Java 포맷 적용 범위에서 제외함.

## 실행

프로젝트 폴더 `outputs/apartment-search`에서 Java 21을 설정한 후 실행함.

```sh
# 스타일 적용
python3 scripts/format-java.py

# 변경 없이 검사 (불일치하면 종료 코드 1)
python3 scripts/format-java.py --check
```

최초 실행 시 Maven Central에서 고정 버전 포맷터를 다운로드함. 다운로드와 캐시 파일의 SHA-256을 검사하며 도구 파일은 Git에서 제외된 `.tools`에 보관함. 이후에는 캐시를 재사용함.

IntelliJ의 EditorConfig 지원을 켜면 Java 들여쓰기 4칸을 사용할 수 있음. IDE 기본 Reformat과 포맷터의 결과가 다를 수 있으므로 커밋 전 위 명령으로 정리함. CI에서도 동일한 검사 명령을 실행함.
