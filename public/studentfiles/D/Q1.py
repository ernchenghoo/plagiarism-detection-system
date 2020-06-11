import cv2
import numpy as np
from matplotlib import pyplot as pt

book = cv2.imread("omega_book.bmp", 0)

book = book * 2
book = book + 15




hist_book = cv2.calcHist([book],[0],None,[256],[0,256])

book2 = cv2.imread("book.bmp", 0)
book_eq = cv2.equalizeHist(book)
book_eq_hist = cv2.calcHist([book_eq],[0],None,[256],[0,256])

pt.figure()
pt.subplot(1,2,1)
pt.title("Omega_Book_Manually_Adj")
pt.xlabel("Bins")
pt.ylabel("Number of Pixels")
pt.plot(hist_book)
pt.xlim([0,256])
pt.subplot(1,2,2)
pt.title("Omega_Book_Equalized")
pt.xlabel("Bins")
pt.ylabel("Number of Pixels")
pt.plot(book_eq_hist)
pt.xlim([0,256])
pt.show()