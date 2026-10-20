package pe.edu.utp.icastay.user;
import java.util.List;
public record SuperadminUserPage(List<SuperadminUserView> content, int page, int size, long totalElements, int totalPages) {}
