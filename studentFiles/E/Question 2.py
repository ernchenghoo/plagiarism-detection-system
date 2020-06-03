from matplotlib import pyplot as pt
import cv2

airplane = cv2.imread("airplane.bmp", 1)

r = airplane[:,:,0]
g = airplane[:,:,1]
b = airplane[:,:,2]

r_hist = cv2.calcHist([b],[0],None,[256],[0,256])
g_hist = cv2.calcHist([g],[0],None,[256],[0,256])
b_hist = cv2.calcHist([r],[0],None,[256],[0,256])

pt.figure()

pt.title("Red Channel")
pt.xlabel("Bins")
pt.ylabel("Count")

pt.plot(b_hist, 'r')
pt.xlim([0,256])

pt.show()

pt.figure()

pt.title("Green Channel")
pt.xlabel("Bins")
pt.ylabel("Count")

pt.plot(g_hist, 'g')
pt.xlim([0,256])

pt.show()

pt.figure()

pt.title("Blue Channel")
pt.xlabel("Bins")
pt.ylabel("Count")

pt.plot(r_hist, 'b')
pt.xlim([0,256])

pt.show()