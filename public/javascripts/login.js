$(document).ready(function() {
    $('#loginPageAlert').hide();
    $('.navigation_bar').hide();
    console.log(url);
    //login ajax
    $("#login-form").submit(function(e) {
        e.preventDefault();
        var formData = new FormData(this);

        $.ajax({
            method: 'POST',
            url: document.location.href + 'login',
            data: formData,
            processData: false,
            contentType: false,
            success : function(response) {
                console.log(response);
                if (response === "Pass") {
                    window.location = 'https://plagiarism-detection-system.herokuapp.com/home';
                }
                else {
                    $('#loginPageAlert').fadeIn(300).delay(3000).fadeOut(300).text(response);
                }
            }
        });
    });
});