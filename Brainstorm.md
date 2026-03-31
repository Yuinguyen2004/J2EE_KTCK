Đề tài: hệ thống quản lí tiệm bi-da
Khởi tạo dự án: 
	- backend: java spring boot
	- frontend: reactjs + tailwindCSS
	- database: postgresSQL
Chức năng chính:
 
Đăng ký, đăng nhập và xác thực người dùng bằng JWT (google firebase)

Quản lý Bàn và Trạng thái Trực quan ( hiển thị bàn theo sơ đồ). click vào bàn để thực hiện các chức năng tương ứng như:
	- Khi kích vào bàn đang trống thì có thể chọn bắt đầu chơi để có thể tính thời gian cho bàn đó để tính tiền
	- Nếu bàn đó đã kích hoatj rồi thì có thể kết thúc và chức năng tính tiền hoạt động sẽ tính dựa vào thời gian bàn đã hoạt động
	- Chúng ta có thể set đặt bàn trước cho các khách đã đặt bàn trước giúp giữ bàn.
	- Nếu như khách có công việc riêng hay việc gì mà yêu cầu tạm ngưng thì cũng có thể pause time lại.

Tính tiền Tự động (Time Tracking): Dựa vào thời gian mà bàn đó đã chơi được bao lâu và có thể tính ra thành tiền. Đồng thời có tính năng tính theo block (ví dụ như 15p đầu sẽ được mức giá này còn lại sẽ mức giá khác) và dựa vào cấp độ mà khách đăng ký từ VIP đến PRO thì sẽ nhận được mức giá ưu đãi khác.

Quản lý Dịch vụ đi kèm (F&B): quản lý menu đồ ăn thức uống của tiệm khi khách đặt đồ ăn.

Quản lý Khách hàng & Thành viên (CRM): Phần này cho phép khách hàng có thể đăng ký thành viên từ VIP đến PRO và tùy vào mức độ sẽ có mức ưu đãi khác nhau

Báo cáo & Quản lý Doanh thu: Nhận lại báo cáo danh thu theo (ngày, tuần, tháng, năm)