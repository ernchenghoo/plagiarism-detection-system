$(document).ready(function() {
    $('#loginPageAlert').hide();
    $('.navigation_bar').hide();

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
            headers: {  'Access-Control-Allow-Origin': 'http://The web site allowed to access' },
            success : function(response) {
                console.log(response);
                if (response === "Pass") {
                    window.location = document.location.href + 'home';
                }
                else {
                    $('#loginPageAlert').fadeIn(300).delay(3000).fadeOut(300).text(response);
                }
            }
        });
    });
});