import numpy as np
import cv2
from matplotlib import pyplot as pt


q4 = cv2.imread("q4omega.tif",1)
q4_gray = cv2.cvtColor(q4,cv2.COLOR_BGR2GRAY)
hist = cv2.calcHist([q4_gray],[0],None,[256],[0,256])

[nrow,ncol] = q4_gray.shape
mask = np.zeros((nrow,ncol),dtype=np.uint8)

mask2 = q4_gray.copy()



threshold = 175
threshold1 = 80

for x in range (0,nrow):
    for y in range(0,ncol):
        if q4_gray[x,y] <= threshold:
            mask[x,y] =255
        if q4_gray[x,y] >=threshold1:
            mask2[x,y]=255
        else:
            mask[x,y]=0
            mask2[x,y]=0

mask3= cv2.bitwise_not(mask)
mask[:,762:1147]=0
mask2[:,0:387]=0
mask2[:,760:1147]=0
mask3[:,0:776]=0
mask4=mask.copy()
mask4=mask+mask2+mask3

ex_q4 = q4.copy()
ex_q4[:,:,0] = cv2.bitwise_and(q4[:,:,0],mask4)
ex_q4[:,:,1] = cv2.bitwise_and(q4[:,:,1],mask4)
ex_q4[:,:,2] = cv2.bitwise_and(q4[:,:,2],mask4)

pt.figure()

pt.subplot(1,2,1)
pt.imshow(cv2.cvtColor(q4, cv2.COLOR_BGR2RGB))
pt.title("Original Color Image")

pt.subplot(1,2,2)
pt.title("Extracted Color Image ")
pt.imshow(cv2.cvtColor(ex_q4, cv2.COLOR_BGR2RGB))
