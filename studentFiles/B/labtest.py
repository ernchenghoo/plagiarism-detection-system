from matplotlib import pyplot as pt
import numpy as np
import cv2

"""question 1
aa = cv2.imread("omega_book.bmp",0)
aa_eq = cv2.equalizeHist(aa)

hist = cv2.calcHist([aa], [0], None, [256], [0,256])
hist_eq = cv2.calcHist([aa_eq], [0], None, [256], [0,256])

pt.figure()
pt.subplot(2,2,1)
pt.imshow(aa, cmap="gray")
pt.title("Before")

pt.subplot(2,2,2)
pt.imshow(aa_eq, cmap="gray")
pt.title("After")
pt.show()

pt.subplot(2,2,3)
pt.title("Before")
pt.plot(hist)
pt.xlim([0,256])

pt.subplot(2,2,4)
pt.title("After")
pt.plot(hist_eq)
pt.xlim([0,256])

pt.show()
"""
"""question 2
airplane = cv2.imread("airplane.bmp")
airplane1 = airplane.copy()

airplane_R = np.zeros((512,512),dtype=np.uint32)
airplane_G = np.zeros((512,512),dtype=np.uint32)
airplane_B = np.zeros((512,512),dtype=np.uint32)

airplane_R = airplane1[0:512,0:512,0]
airplane_G = airplane1[0:512,0:512,1]
airplane_B = airplane1[0:512,0:512,2]

hist1 = cv2.calcHist(airplane,[0],None,[256],[0,256])
hist2 = cv2.calcHist(airplane,[1],None,[256],[0,256])
hist3 = cv2.calcHist(airplane,[2],None,[256],[0,256])


pt.subplot(1,3,1)
pt.title("Red channel")
pt.plot(hist1)

pt.subplot(1,3,2)
pt.title("Green channel")
pt.plot(hist2)

pt.subplot(1,3,3)
pt.title("Blue channel")
pt.plot(hist3)
"""
"""question 3
circuit = cv2.imread("omega_circuit.bmp",0)

[nrow,ncol] = circuit.shape
mask = np.zeros((nrow,ncol),dtype=np.uint8)
mask[64:384,384:64] = 255

bitand_circuit = cv2.bitwise_xor(circuit,mask)
cv2.imshow("Masked circuit", bitand_circuit)
cv2.waitKey()
cv2.destroyAllWindows()
"""
"""question 4
omega = cv2.imread("q4omega.tif",1)
omega_gray = cv2.cvtColor(omega,cv2.COLOR_BGR2GRAY)
hist = cv2.calcHist([omega_gray],[0],None,[256],[0,256])

[nrow,ncol] = omega_gray.shape
mask1 = np.zeros((nrow,ncol),dtype=np.uint8)

mask2 = omega_gray.copy()

threshold = 175
threshold1 = 80

for x in range (0,nrow):
    for y in range(0,ncol):
        if omega_gray[x,y] <= threshold:
            mask1[x,y] =255
        if omega_gray[x,y] >=threshold1:
            mask2[x,y]=255
        else:
            mask1[x,y]=0
            mask2[x,y]=0

mask3= cv2.bitwise_not(mask1)
mask1[:,762:1147]=0
mask2[:,0:387]=0
mask2[:,760:1147]=0
mask3[:,0:776]=0
mask4=mask1.copy()
mask4=mask1+mask2+mask3

exq4 = omega.copy()
exq4[:,:,0] = cv2.bitwise_and(omega[:,:,0],mask4)
exq4[:,:,1] = cv2.bitwise_and(omega[:,:,1],mask4)
exq4[:,:,2] = cv2.bitwise_and(omega[:,:,2],mask4)

pt.figure()

pt.subplot(1,2,1)
pt.imshow(cv2.cvtColor(q4, cv2.COLOR_BGR2RGB))
pt.title("Original Color Image")

pt.subplot(1,2,2)
pt.title("Extracted Color Image ")
pt.imshow(cv2.cvtColor(exq4, cv2.COLOR_BGR2RGB))
"""