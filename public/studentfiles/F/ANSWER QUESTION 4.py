import numpy as np
import cv2
from matplotlib import pyplot as pt

img = cv2.imread("q4omega.tif",1)
img_gray = cv2.cvtColor(img,cv2.COLOR_BGR2GRAY)
hist = cv2.calcHist([img_gray],[0],None,[256],[0,256])

[nrow,ncol] = img_gray.shape
mask = np.zeros((nrow,ncol),dtype=np.uint8)

mask2 = img_gray.copy()

t1 = 178
t2 = 80

for x in range (0,nrow):
    for y in range(0,ncol):
        if img_gray[x,y] <= t1:
            mask[x,y] =255
        if img_gray[x,y] >=t2:
            mask2[x,y]=255
        else:
            mask[x,y]=0
            mask2[x,y]=0

mask3= cv2.bitwise_not(mask)

mask[:,760:1147]=0

mask2[:,0:385]=0

mask2[:,760:1145]=0

mask3[:,0:775]=0

mask4=mask.copy()

mask4=mask+mask2+mask3

ex_img = img.copy()
ex_img[:,:,0] = cv2.bitwise_and(img[:,:,0],mask4)
ex_img[:,:,1] = cv2.bitwise_and(img[:,:,1],mask4)
ex_img[:,:,2] = cv2.bitwise_and(img[:,:,2],mask4)

pt.figure()

pt.subplot(3,3,1)
pt.imshow(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
pt.title("Original Color Image")

pt.subplot(3,3,2)
pt.imshow(img_gray, cmap="gray")
pt.title("Original Gray Image")

pt.subplot(3,3,3)
pt.imshow(mask, cmap="gray")
pt.title("Mask 1")

pt.subplot(3,3,4)
pt.imshow(mask2, cmap="gray")
pt.title("Mask 2")

pt.subplot(3,3,5)
pt.imshow(mask3, cmap="gray")
pt.title("Mask 3")

pt.subplot(3,3,6)
pt.imshow(mask4, cmap="gray")
pt.title("Mask 4")

pt.subplot(3,3,7)
pt.title("Extracted Color Image ")
pt.imshow(cv2.cvtColor(ex_img, cv2.COLOR_BGR2RGB))

pt.subplot(3,3,8)
pt.title("Histogram")
pt.plot(hist)
pt.xlim([0,256])

pt.show()