import numpy as np
import cv2
from matplotlib import pyplot as pt
#question 1

book = cv2.imread("omega_book.bmp",0)
book = book -17*2
book_eq = cv2.equalizeHist(book)

hist_book = cv2.calcHist([book],[0],None,[256],[0,256])
hist_book_eq = cv2.calcHist([book_eq],[0],None,[256],[0,256])

pt.figure()
pt.subplot(2,2,1)
pt.imshow(book,cmap="gray")
pt.title("manually adjusted")

pt.subplot(2,2,2)
pt.imshow(book_eq,cmap="gray")
pt.title("histogram equalized version")

pt.subplot(2,2,3)
pt.plot(hist_book)
pt.title("manually adjusted histogram")

pt.subplot(2,2,4)
pt.plot(hist_book_eq)
pt.title("histogram equalized version")
pt.show()
