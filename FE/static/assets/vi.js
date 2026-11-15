'use strict';
// Chỉ dịch thông báo hệ thống để hiển thị; API và dữ liệu vẫn dùng tiếng Anh.
const SERVER_MESSAGES_VI = {
  "No weekly due tasks.": "Không có công việc đến hạn trong tuần.",
  "Status is required.": "Vui lòng chọn trạng thái.",
  "Live / awaiting Friday 22:00 checkpoint.": "Đang cập nhật / chờ chốt báo cáo lúc 22:00 thứ Sáu.",
  "Every due task is completed or self-reported ACHIEVED.": "Các công việc đến hạn đã hoàn thành hoặc được thành viên tự báo cáo đạt mục tiêu.",
  "No completed tasks and no weekly reports in this cohort.": "Chưa có công việc hoàn thành hoặc báo cáo nào trong các công việc đến hạn của kỳ này.",
  "Some work is completed or reported; other required reports are missing or NOT_ACHIEVED.": "Đã có công việc hoàn thành hoặc được báo cáo, nhưng vẫn còn báo cáo chưa nộp hoặc kết quả chưa đạt mục tiêu.",
  "Checkpoint date must be a Friday (Vietnam time).": "Ngày báo cáo phải là thứ Sáu theo giờ Việt Nam.",
  "Task scope must be mine or team.": "Phạm vi công việc phải là của tôi hoặc của nhóm.",
  "Start date and deadline are required.": "Vui lòng nhập ngày bắt đầu và hạn hoàn thành.",
  "Deadline must be on or after the start date.": "Hạn hoàn thành phải từ ngày bắt đầu trở đi.",
  "Assignee must belong to this project.": "Người thực hiện phải là thành viên của dự án này.",
  "This creation request ID has already been used for a different task. Refresh and try again.": "Mã yêu cầu này đã được dùng để tạo công việc khác. Vui lòng tải lại trang và thử lại.",
  "Confirmed plans are locked. The TL must reopen the draft first.": "Kế hoạch đã xác nhận được khóa chỉnh sửa. Trưởng nhóm cần mở lại bản nháp trước.",
  "This plan is already confirmed.": "Kế hoạch này đã được xác nhận.",
  "This plan is already a draft.": "Kế hoạch này đã ở trạng thái bản nháp.",
  "Confirm the plan before changing execution status.": "Vui lòng xác nhận kế hoạch trước khi cập nhật trạng thái thực hiện.",
  "Rationale for TL status correction": "Lý do trưởng nhóm điều chỉnh trạng thái",
  "Reports can only be saved on this Friday before 22:00 Vietnam time.": "Chỉ có thể lưu báo cáo vào thứ Sáu của kỳ này trước 22:00 giờ Việt Nam.",
  "This task does not require a report for the selected checkpoint.": "Công việc này không cần báo cáo trong kỳ đã chọn.",
  "Checkpoint result is required.": "Vui lòng chọn kết quả báo cáo.",
  "Evidence URL must be an http or https URL.": "Đường dẫn minh chứng phải bắt đầu bằng http hoặc https và có địa chỉ hợp lệ.",
  "This support request is already resolved.": "Yêu cầu hỗ trợ này đã được xử lý.",
  "The TL marked your request resolved.": "Trưởng nhóm đã đánh dấu yêu cầu của bạn được xử lý.",
  "You do not have permission for this action.": "Bạn không có quyền thực hiện thao tác này.",
  "The requested item is unavailable.": "Nội dung được yêu cầu không tồn tại hoặc không thể truy cập.",
  "Check the submitted values, IDs and dates.": "Vui lòng kiểm tra các giá trị, mã định danh và ngày tháng đã nhập.",
  "This change conflicts with database constraints. Refresh and check the current state.": "Thay đổi này không phù hợp với ràng buộc dữ liệu. Vui lòng tải lại trang và kiểm tra trạng thái hiện tại.",
  "The server could not complete this request. Please try again.": "Máy chủ chưa thể xử lý yêu cầu. Vui lòng thử lại.",
  "Please sign in.": "Vui lòng đăng nhập.",
  "Access denied or CSRF token missing. Refresh and try again.": "Bạn không có quyền truy cập hoặc phiên bảo mật chưa hợp lệ. Vui lòng tải lại trang và thử lại.",
  "Invalid credentials": "Thông tin đăng nhập không hợp lệ",
  "Job must be remind or finalize.": "Vui lòng chọn tác vụ nhắc báo cáo hoặc tổng kết.",
  "Friday checkpoint needs review": "Báo cáo tuần cần trưởng nhóm xem xét",
  "Friday checkpoint reminder": "Nhắc nộp báo cáo tuần"
};
const FIELD_LABELS_VI = {
  "Title": "Tiêu đề",
  "Description": "Mô tả",
  "Expected output": "Kết quả cần bàn giao",
  "Rationale for TL status correction": "Lý do trưởng nhóm điều chỉnh trạng thái",
  "Note": "Ghi chú",
  "Evidence URL": "Đường dẫn minh chứng",
  "Reason": "Lý do",
  "Requested help": "Nội dung cần hỗ trợ",
  "Leader note": "Ghi chú của trưởng nhóm"
};
function translateServerText(message) {
  if(SERVER_MESSAGES_VI[message]) return SERVER_MESSAGES_VI[message];
  let match=String(message || '').match(/^(.+) is required\.$/);
  if(match && FIELD_LABELS_VI[match[1]]) return `Vui lòng nhập ${FIELD_LABELS_VI[match[1]].toLocaleLowerCase('vi-VN')}.`;
  match=String(message || '').match(/^(.+) must be at most (\d+) characters\.$/);
  if(match && FIELD_LABELS_VI[match[1]]) return `${FIELD_LABELS_VI[match[1]]} không được vượt quá ${match[2]} ký tự.`;
  return message;
}
