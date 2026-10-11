# NavCert Offline: verified references

Numbered in IEEE style. Every entry was checked in October 2026 against Crossref (DOI records), the arXiv API, or the publisher or organisation page given as the link. Where a detail could not be confirmed it was left out (page ranges and issue numbers for older print sources, for example). Section 1 records the corrections made to the first paper draft.

## 1. Re-verification of the original paper's 20 references

| Original | Now | Result |
|---|---|---|
| [1] | [1] | Confirmed (Crossref). |
| [2] | [2] | Corrected: the Crossref record dates the chapter 2010 (LNICST volume); the draft said 2009, which is the conference year. Pages 273-292 confirmed. Venue given as Lecture Notes of the ICST. |
| [3] | [3] | Confirmed (arXiv API). |
| [4] | [4] | Confirmed (arXiv API). |
| [5] | [5] | Confirmed (arXiv API). |
| [6] | [6] | Confirmed: arXiv:2603.24373 exists, submitted 25 Mar 2026, first author Cheng Cui, 14 authors. Abstract matches the title (5M parameters). |
| [7] | [13] | Confirmed (Crossref). |
| [8] | [15] | Confirmed (Crossref). |
| [9] | [16] | Confirmed (Crossref). |
| [10] | [14] | Confirmed (Crossref; arXiv 2111.15664). |
| [11] | [27] | Confirmed (Crossref). Author written M.-W. Chang. |
| [12] | [28] | Confirmed (Crossref). |
| [13] | [19] | Confirmed (Crossref). |
| [14] | [12] | Corrected: pages are 9357-9366 (Crossref), not 9365-9374 as in the draft; DOI 10.1109/CVPR.2019.00959 added. |
| [15] | [11] | Confirmed (Crossref). |
| [16] | [17] | Confirmed (arXiv API; first author Haoran Wei). |
| [17] | [18] | Confirmed: arXiv:2606.23050 exists, submitted 22 Jun 2026, first author Youyang Yin, 17 authors. Abstract matches (reference sliding-window attention on DeepSeek-OCR). |
| [18] | [20] | Confirmed: NeurIPS 2019 Document Intelligence Workshop, first author Seunghyun Park (mlanthology entry). |
| [19] | [36] | Kept: Dr. Dobb's Journal, Nov. 2000 (listing confirmed through an OpenCV forum thread; the article text is not online). |
| [20] | [35] | Kept with the year replaced by an access date: the page carries no publication year. |

No original reference was dropped. Both arXiv items flagged for checking (2603.24373 and 2606.23050) exist.

Additions that could not be fully verified are labelled in the entry: the Sobel and Feldman operator was never formally published; the original three-step-search paper by Koga et al. could not be located, so the IEEE TCSVT paper by Li, Zeng and Liou is cited instead; the Paris MoU figure is taken from a secondary summary.

## 2. Reference list

### A. OCR engines and text recognition

[1] R. Smith, "An overview of the Tesseract OCR engine," in *Proc. 9th Int. Conf. Document Analysis and Recognition (ICDAR)*, vol. 2, 2007, pp. 629-633. https://doi.org/10.1109/ICDAR.2007.4376991  
*Supports:* Classic open-source OCR baseline; Tesseract 4 added an LSTM line recogniser (project README).

[2] M. Zhang, A. Joshi, R. Kadmawala, K. Dantu, S. Poduri, and G. S. Sukhatme, "OCRdroid: A framework to digitize text using mobile phones," in *Mobile Computing, Applications, and Services*, Lecture Notes of the ICST, 2010, pp. 273-292. https://doi.org/10.1007/978-3-642-12607-9_18  
*Supports:* Early phone-based OCR pipeline; prior art for camera capture on mobile.

[3] Y. Du *et al.*, "PP-OCR: A practical ultra lightweight OCR system," arXiv:2009.09941, 2020. https://arxiv.org/abs/2009.09941  
*Supports:* Detection plus recognition design of the ultra-light PP-OCR family.

[4] Y. Du *et al.*, "PP-OCRv2: Bag of tricks for ultra lightweight OCR system," arXiv:2109.03144, 2021. https://arxiv.org/abs/2109.03144  
*Supports:* Second PP-OCR generation.

[5] C. Li *et al.*, "PP-OCRv3: More attempts for the improvement of ultra lightweight OCR system," arXiv:2206.03001, 2022. https://arxiv.org/abs/2206.03001  
*Supports:* Third PP-OCR generation.

[6] C. Cui *et al.*, "PP-OCRv5: A specialized 5M-parameter model rivaling billion-parameter vision-language models on OCR tasks," arXiv:2603.24373, Mar. 2026. https://arxiv.org/abs/2603.24373  
*Supports:* Abstract states a 5M-parameter OCR model competitive with billion-parameter VLMs; used for handwriting via ONNX Runtime.

[7] C. Cui *et al.*, "PaddleOCR 3.0 technical report," arXiv:2507.05595, Jul. 2025. https://arxiv.org/abs/2507.05595  
*Supports:* Toolkit paper covering PP-OCRv5, PP-StructureV3 and PP-ChatOCRv4; models under 100M parameters.

[8] PaddlePaddle Team, "PP-OCRv6 release," Hugging Face Blog, Jun. 22, 2026. https://huggingface.co/blog/PaddlePaddle/pp-ocrv6  
*Supports:* Tiny/small/medium tiers (1.5M/7.7M/34.5M parameters) with ONNX variants; next OCR challenger.

[9] B. Shi, X. Bai, and C. Yao, "An end-to-end trainable neural network for image-based sequence recognition and its application to scene text recognition," *IEEE Trans. Pattern Anal. Mach. Intell.*, vol. 39, pp. 2298-2304, 2017. https://doi.org/10.1109/TPAMI.2016.2646371  
*Supports:* CRNN: the CNN + recurrent + CTC recogniser design behind most line-level OCR.

[10] A. Graves *et al.*, "Connectionist temporal classification: Labelling unsegmented sequence data with recurrent neural networks," in *Proc. 23rd Int. Conf. Machine Learning (ICML)*, 2006, pp. 369-376. https://doi.org/10.1145/1143844.1143891  
*Supports:* CTC loss that lets line recognisers train without character-level alignment.

[11] M. Liao, Z. Wan, C. Yao, K. Chen, and X. Bai, "Real-time scene text detection with differentiable binarization," in *Proc. AAAI Conf. Artificial Intelligence*, vol. 34, no. 7, 2020, pp. 11474-11481. https://doi.org/10.1609/aaai.v34i07.6812  
*Supports:* DB text detector used inside PP-OCR; binarisation inside the network.

[12] Y. Baek, B. Lee, D. Han, S. Yun, and H. Lee, "Character region awareness for text detection," in *Proc. IEEE/CVF Conf. Computer Vision and Pattern Recognition (CVPR)*, 2019, pp. 9357-9366. https://doi.org/10.1109/CVPR.2019.00959  
*Supports:* Character-level region/affinity text detection (CRAFT).

[13] M. Li *et al.*, "TrOCR: Transformer-based optical character recognition with pre-trained models," in *Proc. AAAI Conf. Artificial Intelligence*, vol. 37, no. 11, 2023, pp. 13094-13102. https://doi.org/10.1609/aaai.v37i11.26538  
*Supports:* Transformer line recogniser; larger alternative to CRNN-style models.

[14] G. Kim *et al.*, "OCR-free document understanding transformer," in *Computer Vision - ECCV 2022*, LNCS, vol. 13688, 2022, pp. 498-517. https://doi.org/10.1007/978-3-031-19815-1_29  
*Supports:* Image-to-structured-output document understanding without a separate OCR stage.

[15] Y. Xu *et al.*, "LayoutLM: Pre-training of text and layout for document image understanding," in *Proc. 26th ACM SIGKDD Int. Conf. Knowledge Discovery and Data Mining*, 2020, pp. 1192-1200. https://doi.org/10.1145/3394486.3403172  
*Supports:* Joint text and 2-D layout pre-training for forms and documents.

[16] Y. Huang, T. Lv, L. Cui, Y. Lu, and F. Wei, "LayoutLMv3: Pre-training for document AI with unified text and image masking," in *Proc. 30th ACM Int. Conf. Multimedia*, 2022, pp. 4083-4091. https://doi.org/10.1145/3503161.3548112  
*Supports:* Layout-aware baseline considered and deferred (size, annotation cost).

[17] H. Wei *et al.*, "DeepSeek-OCR: Contexts optical compression," arXiv:2510.18234, Oct. 2025. https://arxiv.org/abs/2510.18234  
*Supports:* Vision-language OCR via optical context compression; heavy server-class alternative.

[18] Y. Yin *et al.*, "Unlimited OCR works," arXiv:2606.23050, Jun. 2026. https://arxiv.org/abs/2606.23050  
*Supports:* Reference sliding-window attention for multi-page OCR decoding on top of DeepSeek-OCR.

[19] G. Jaume, H. K. Ekenel, and J.-P. Thiran, "FUNSD: A dataset for form understanding in noisy scanned documents," in *Proc. ICDAR Workshops*, 2019, pp. 1-6. https://doi.org/10.1109/ICDARW.2019.10029  
*Supports:* Benchmark for key-value understanding in noisy scans.

[20] S. Park *et al.*, "CORD: A consolidated receipt dataset for post-OCR parsing," in *Workshop on Document Intelligence at NeurIPS*, 2019. https://mlanthology.org/neuripsw/2019/park2019neuripsw-cord  
*Supports:* Receipt dataset with OCR boxes and semantic labels for parsing.

### B. Document capture, rectification and mobile document datasets

[21] K. Ma *et al.*, "DocUNet: Document image unwarping via a stacked U-Net," in *Proc. IEEE/CVF CVPR*, 2018, pp. 4700-4709. https://doi.org/10.1109/CVPR.2018.00494  
*Supports:* Learning-based dewarping of curved photographed pages; contrast to classical homography.

[22] H. Feng, Y. Wang, W. Zhou *et al.*, "DocTr: Document image transformer for geometric unwarping and illumination correction," in *Proc. 29th ACM Int. Conf. Multimedia*, 2021, pp. 273-281. https://doi.org/10.1145/3474085.3475388  
*Supports:* Transformer dewarping with illumination correction.

[23] J.-C. Burie *et al.*, "ICDAR2015 competition on smartphone document capture and OCR (SmartDoc)," in *Proc. 13th Int. Conf. Document Analysis and Recognition*, 2015, pp. 1161-1165. https://doi.org/10.1109/ICDAR.2015.7333943  
*Supports:* Smartphone document capture: page detection and OCR on phone-captured pages.

[24] D. Karatzas *et al.*, "ICDAR 2015 competition on robust reading," in *Proc. 13th Int. Conf. Document Analysis and Recognition*, 2015, pp. 1156-1160. https://doi.org/10.1109/ICDAR.2015.7333942  
*Supports:* Robust Reading evaluation protocol for text in uncontrolled images.

[25] V. V. Arlazarov *et al.*, "MIDV-500: A dataset for identity document analysis and recognition on mobile devices in video stream," *Computer Optics*, vol. 43, no. 5, pp. 818-824, 2019. https://doi.org/10.18287/2412-6179-2019-43-5-818-824  
*Supports:* Mobile video of identity documents under varied conditions.

[26] K. Bulatov *et al.*, "MIDV-2020: A comprehensive benchmark dataset for identity document analysis," arXiv:2107.00396, 2021. https://arxiv.org/abs/2107.00396  
*Supports:* Larger mobile identity-document benchmark with photos, scans and video.

### C. On-device machine learning and vision libraries

[27] J. Devlin, M.-W. Chang, K. Lee, and K. Toutanova, "BERT: Pre-training of deep bidirectional transformers for language understanding," in *Proc. NAACL-HLT*, 2019, pp. 4171-4186. https://doi.org/10.18653/v1/N19-1423  
*Supports:* Foundation of extractive question answering over text.

[28] Z. Sun *et al.*, "MobileBERT: A compact task-agnostic BERT for resource-limited devices," in *Proc. 58th Annu. Meeting Assoc. Computational Linguistics*, 2020, pp. 2158-2170. https://doi.org/10.18653/v1/2020.acl-main.195  
*Supports:* Compact BERT for phones; the app's document Q&A and fallback model.

[29] W. Wang *et al.*, "MiniLM: Deep self-attention distillation for task-agnostic compression of pre-trained transformers," arXiv:2002.10957, 2020. https://arxiv.org/abs/2002.10957  
*Supports:* Distilled transformer used as the base of the certificate-field model.

[30] P. Rajpurkar, R. Jia, and P. Liang, "Know what you don't know: Unanswerable questions for SQuAD," in *Proc. 56th Annu. Meeting ACL (Short Papers)*, 2018, pp. 784-789. https://doi.org/10.18653/v1/P18-2124  
*Supports:* SQuAD 2.0: extractive QA with unanswerable questions, which suits missing-field abstention.

[31] deepset, "deepset/minilm-uncased-squad2," Hugging Face model card, accessed Oct. 2026. https://huggingface.co/deepset/minilm-uncased-squad2  
*Supports:* Base checkpoint: MiniLM-L12-H384, CC-BY-4.0, SQuAD 2.0 dev EM 76.13 / F1 79.50 on the card.

[32] B. Jacob *et al.*, "Quantization and training of neural networks for efficient integer-arithmetic-only inference," in *Proc. IEEE/CVF CVPR*, 2018, pp. 2704-2713. https://doi.org/10.1109/CVPR.2018.00286  
*Supports:* Integer quantisation; basis for the int8 ONNX model.

[33] Microsoft, "ONNX Runtime Mobile," ONNX Runtime documentation, accessed Oct. 2026. https://onnxruntime.ai/docs/tutorials/mobile/  
*Supports:* Android package onnxruntime-android; CPU default with NNAPI and XNNPACK options.

[34] Google, "LiteRT overview," Google AI Edge documentation (built on TensorFlow Lite), accessed Oct. 2026. https://developers.google.com/edge/litert  
*Supports:* On-device inference framework of Google's mobile ML stack; alternative runtime.

[35] Google, "Text recognition v2," ML Kit for Android, accessed Oct. 2026. https://developers.google.com/ml-kit/vision/text-recognition/v2/android  
*Supports:* Bundled model works offline; input guidance (16x16 px characters, focus).

[36] G. Bradski, "The OpenCV library," *Dr. Dobb's Journal of Software Tools*, Nov. 2000. https://forum.opencv.org/t/opencv-article-in-dr-dobbs-journal/8962  
*Supports:* Original OpenCV article (listing confirmed by an OpenCV forum thread; the magazine text itself is not online).

### D. Image and video processing: textbooks and primary sources

[37] R. C. Gonzalez and R. E. Woods, *Digital Image Processing*, 4th ed. New York, NY, USA: Pearson, 2018 (ISBN 978-0-13-335672-4). https://www.pearson.com/en-us/subject-catalog/p/digital-image-processing/P200000003518/9780137848560  
*Supports:* Course textbook for all IVP units (Pearson catalogue page lists the 4th edition and this ISBN).

[38] W. Burger and M. J. Burge, *Digital Image Processing: An Algorithmic Introduction*, 2nd ed. Cham, Switzerland: Springer, 2022. https://doi.org/10.1007/978-3-031-05744-1  
*Supports:* Course textbook with algorithm-level descriptions.

[39] N. Otsu, "A threshold selection method from gray-level histograms," *IEEE Trans. Syst., Man, Cybern.*, vol. SMC-9, pp. 62-66, 1979. https://doi.org/10.1109/TSMC.1979.4310076  
*Supports:* Otsu thresholding (between-class variance).

[40] J. Canny, "A computational approach to edge detection," *IEEE Trans. Pattern Anal. Mach. Intell.*, vol. PAMI-8, pp. 679-698, 1986. https://doi.org/10.1109/TPAMI.1986.4767851  
*Supports:* Canny edge detector (smoothing, gradient, non-maximum suppression, hysteresis).

[41] I. Sobel and G. Feldman, "A 3x3 isotropic gradient operator for image processing," talk at the Stanford Artificial Intelligence Project, 1968 (never formally published; first credited in R. O. Duda and P. E. Hart, *Pattern Classification and Scene Analysis*, 1973, pp. 271-272). https://en.wikipedia.org/wiki/Sobel_operator  
*Supports:* Sobel gradient operator; history confirmed from Sobel's own account summarised on the page.

[42] J. M. S. Prewitt, "Object enhancement and extraction," in *Picture Processing and Psychopictorics*, B. S. Lipkin and A. Rosenfeld, Eds. New York, NY, USA: Academic Press, 1970. https://www.elsevier.com/books/picture-processing-and-psychopictorics/lipkin/978-0-12-451550-5  
*Supports:* Prewitt gradient operator (chapter listed in the volume's contents).

[43] D. Marr and E. Hildreth, "Theory of edge detection," *Proc. R. Soc. Lond. B*, vol. 207, pp. 187-217, 1980. https://doi.org/10.1098/rspb.1980.0020  
*Supports:* Laplacian-of-Gaussian zero-crossing edge detection.

[44] S. M. Pizer *et al.*, "Adaptive histogram equalization and its variations," *Comput. Vis., Graph., Image Process.*, vol. 39, pp. 355-368, 1987. https://doi.org/10.1016/S0734-189X(87)80186-X  
*Supports:* Adaptive histogram equalisation (AHE).

[45] K. Zuiderveld, "Contrast limited adaptive histogram equalization," in *Graphics Gems IV*, P. S. Heckbert, Ed. San Diego, CA, USA: Academic Press, 1994, pp. 474-485. https://www.gameenginegems.com/gemsdb/article.php?id=970  
*Supports:* CLAHE: clip-limited, tile-based equalisation (bibliographic entry in the Graphics Gems database).

[46] S. Suzuki and K. Abe, "Topological structural analysis of digitized binary images by border following," *Comput. Vis., Graph., Image Process.*, vol. 30, pp. 32-46, 1985. https://doi.org/10.1016/0734-189X(85)90016-7  
*Supports:* Contour following behind OpenCV findContours.

[47] D. H. Douglas and T. K. Peucker, "Algorithms for the reduction of the number of points required to represent a digitized line or its caricature," *Cartographica*, vol. 10, pp. 112-122, 1973. https://doi.org/10.3138/FM57-6770-U75U-7727  
*Supports:* Polyline simplification; used to reduce a page contour to four corners.

[48] R. Hartley and A. Zisserman, *Multiple View Geometry in Computer Vision*, 2nd ed. Cambridge, U.K.: Cambridge Univ. Press, 2004. https://doi.org/10.1017/CBO9780511811685  
*Supports:* Plane-to-plane homography used for perspective rectification.

[49] J. L. Pech-Pacheco *et al.*, "Diatom autofocusing in brightfield microscopy: A comparative study," in *Proc. 15th Int. Conf. Pattern Recognition (ICPR)*, vol. 3, 2000, pp. 314-317. https://doi.org/10.1109/ICPR.2000.903548  
*Supports:* Comparative study of focus measures, the usual citation for Laplacian-variance blur detection.

[50] J. Sauvola and M. Pietikainen, "Adaptive document image binarization," *Pattern Recognit.*, vol. 33, pp. 225-236, 2000. https://doi.org/10.1016/S0031-3203(99)00055-2  
*Supports:* Local-statistics binarisation for documents with uneven illumination.

[51] W. Niblack, *An Introduction to Digital Image Processing*. Englewood Cliffs, NJ, USA: Prentice-Hall, 1986 (ISBN 0-13-480674-3). https://mesa.marmot.org/Record/.b2386803x  
*Supports:* Niblack local threshold (mean + k x local standard deviation); catalogue record for the book.

[52] N. Ahmed, T. Natarajan, and K. R. Rao, "Discrete cosine transform," *IEEE Trans. Comput.*, vol. C-23, pp. 90-93, 1974. https://doi.org/10.1109/T-C.1974.223784  
*Supports:* Original DCT paper.

[53] G. K. Wallace, "The JPEG still picture compression standard," *IEEE Trans. Consum. Electron.*, vol. 38, pp. xviii-xxxiv, 1992. https://doi.org/10.1109/30.125072  
*Supports:* DCT-based JPEG pipeline (8x8 blocks, quantisation, entropy coding).

[54] R. M. Haralick, S. R. Sternberg, and X. Zhuang, "Image analysis using mathematical morphology," *IEEE Trans. Pattern Anal. Mach. Intell.*, vol. PAMI-9, pp. 532-550, 1987. https://doi.org/10.1109/TPAMI.1987.4767941  
*Supports:* Erosion, dilation, opening, closing and applications.

[55] J. Serra, *Image Analysis and Mathematical Morphology*. London, U.K.: Academic Press, 1982. https://doi.org/10.1002/cyto.990040213  
*Supports:* Founding text of mathematical morphology (bibliographic details confirmed through a published review).

[56] J. R. Jain and A. K. Jain, "Displacement measurement and its application in interframe image coding," *IEEE Trans. Commun.*, vol. COM-29, pp. 1799-1808, 1981. https://doi.org/10.1109/TCOM.1981.1094950  
*Supports:* 2-D logarithmic block-matching search.

[57] R. Li, B. Zeng, and M. L. Liou, "A new three-step search algorithm for block motion estimation," *IEEE Trans. Circuits Syst. Video Technol.*, vol. 4, pp. 438-442, 1994. https://doi.org/10.1109/76.313138  
*Supports:* Three-step search for block matching (improved variant; the original Koga et al. conference paper could not be verified).

### E. Problem domain: seafarer certification, fraud and workforce

[58] International Maritime Organization, "International Convention on Standards of Training, Certification and Watchkeeping for Seafarers (STCW)," IMO web page, accessed Oct. 2026. https://www.imo.org/en/ourwork/humanelement/pages/stcw-convention.aspx  
*Supports:* STCW adoption (7 Jul 1978), entry into force (28 Apr 1984), 2010 Manila amendments against certificate fraud.

[59] International Maritime Organization, "Fraudulent certificates," IMO web page, accessed Oct. 2026. https://imo.org/fr/OurWork/HumanElement/Pages/FraudulentCertificates.aspx  
*Supports:* MSC/Circ.900, Assembly resolution A.892(21), STCW regulation I/5, fraud reporting format.

[60] IMO Sub-Committee on Standards of Training and Watchkeeping, "Unlawful practices associated with certificates of competency," Secretariat, doc. STW 32/6 (mirror hosted by NTOU). https://dstm.ntou.edu.tw/var/file/71/1071/img/558/STW32-6-UnlawfulPracticesAssociatedWithCertificatesOfCompetency(Secretariat).pdf  
*Supports:* Primary data: 12,635 detected forgery cases, 54 administrations responding, 82% of survey respondents had detected forged certificates.

[61] International Maritime Organization, "Guidelines on the use of electronic certificates of seafarers," MSC.1/Circ.1665, Jun. 28, 2023. https://admin.soefartsstyrelsen.dk/Media/638684798012087625/MSC.1-Circ.1665 - Guidelines On The Use Of Electronic Certificates Of Seafarers.PDF  
*Supports:* IMO guidance on e-certificates; verification relies on QR code or tracking number and an Internet connection.

[62] BIMCO and International Chamber of Shipping, "Seafarer Workforce Report 2026: Executive summary," Jun. 2026. https://www.bimco.org/media/mxhbsjxb/seafarer-workforce-report-2026-executive-summary.pdf  
*Supports:* Primary workforce figures: supply, demand, fleet size, officer shortage.

[63] BIMCO, "Press release: Seafarer Workforce Report 2026," Jun. 25, 2026. https://www.bimco.org/news-insights/press-media/press-releases/2026/0625-workforce-report/  
*Supports:* Headline numbers: 2.57 million seafarers, 85,148 ships, 113,735 more officers by 2030.

[64] Tokyo MOU Secretariat, *Annual Report on Port State Control in the Asia-Pacific Region 2024*, 2025. https://www.tokyo-mou.org/wp/wp-content/uploads/ANN24-1.pdf  
*Supports:* Inspections, deficiencies, and crew-certificate deficiency counts for 2022-2024.

[65] International Institute of Marine Surveying, "Paris MoU releases 2024 Annual Report," news summary, 2025. https://www.iims.org.uk/?p=55654  
*Supports:* Secondary summary of the Paris MoU 2024 report: detention rate 4.03%.

[66] M. Schuler, "India cracks down on fraudulent seafarer certificates amid global safety concerns," gCaptain, Jul. 21, 2025. https://gcaptain.com/india-cracks-down-on-fraudulent-seafarer-certificates-amid-global-safety-concerns/  
*Supports:* DG Shipping circular of 18 Jul 2025: debarment, CDC suspension, INDoS block, RPSL verification duty.

[67] Marine Insight, "India overtakes China to become world's 2nd largest supplier of seafarers," Aug. 3, 2026. https://www.marineinsight.com/india-overtakes-china-to-become-worlds-2nd-largest-supplier-of-seafarers/  
*Supports:* BIMCO-ICS 2026 figures for India: 311,936 seafarers, 12.16% of the world workforce.

[68] The Maritime Executive, "Record number of Indian seafarers employed," Feb. 28, 2018. https://maritime-executive.com/article/record-number-of-indian-seafarers-employed  
*Supports:* DG Shipping data: 154,349 Indian seafarers at end-2017 (historical baseline).

### F. Android and mobile application development

[69] Google, "Jetpack Compose documentation," Android Developers, accessed Oct. 2026. https://developer.android.com/develop/ui/compose/documentation  
*Supports:* Compose is the modern declarative UI toolkit for Android.

[70] Google, "Material Design 3 in Compose," Android Developers, accessed Oct. 2026. https://developer.android.com/develop/ui/compose/designsystems/material3  
*Supports:* androidx.compose.material3 and MaterialTheme (colour scheme, typography, shapes).

[71] Google, "Material Design 3," m3.material.io, accessed Oct. 2026. https://m3.material.io/foundations  
*Supports:* Official Material 3 foundations and guidelines.

[72] Google, "Target API level requirements for Google Play apps," Play Console Help, accessed Oct. 2026. https://support.google.com/googleplay/android-developer/answer/11926878  
*Supports:* Play requires new apps and updates to target Android 16 (API 36) from 31 Aug 2026.

[73] Google, "Provide information for Google Play's Data safety section," Play Console Help, accessed Oct. 2026. https://support.google.com/googleplay/android-developer/answer/10787469  
*Supports:* Data safety form is mandatory even for apps that collect no data.

[74] Google, "Data and file storage overview," Android Developers, accessed Oct. 2026. https://developer.android.com/training/data-storage  
*Supports:* Scoped storage; app-specific internal storage is private to the app and removed on uninstall.

[75] Google, "Camera intents," Android Developers, accessed Oct. 2026. https://developer.android.com/media/camera/camera-intents  
*Supports:* Capture a photo through the default camera app with MediaStore.ACTION_IMAGE_CAPTURE; no camera library needed.

[76] Google, "CameraX overview," Android Developers, accessed Oct. 2026. https://developer.android.com/media/camera/camerax  
*Supports:* Preview, ImageCapture and ImageAnalysis use cases; supports API 21+.

[77] Google, "Request runtime permissions," Android Developers, accessed Oct. 2026. https://developer.android.com/training/permissions/requesting  
*Supports:* Ask for permission in context; degrade gracefully on denial.

[78] Google, "Intents and intent filters," Android Developers, accessed Oct. 2026. https://developer.android.com/guide/components/intents-filters  
*Supports:* Intent definition and its three uses: activity, service, broadcast.

[79] Google, "Save data in a local database using Room," Android Developers, accessed Oct. 2026. https://developer.android.com/training/data-storage/room  
*Supports:* Room: abstraction over SQLite for offline local data.

### G. Artificial intelligence topics

[80] S. Russell and P. Norvig, *Artificial Intelligence: A Modern Approach*, 4th US ed. Hoboken, NJ, USA: Pearson. https://aima.cs.berkeley.edu/  
*Supports:* Course textbook: agents (Ch. 2), search (Ch. 3-4), CSP (Ch. 6), logic and inference (Ch. 7-9).

[81] V. I. Levenshtein, "Binary codes capable of correcting deletions, insertions, and reversals," *Soviet Physics Doklady*, vol. 10, no. 8, pp. 707-710, 1966 (translation of *Dokl. Akad. Nauk SSSR*, vol. 163, no. 4, pp. 845-848, 1965). https://mathnet.ru/eng/dan31411  
*Supports:* Edit distance used for fuzzy matching of OCR-corrupted labels and names.

[82] E. H. Shortliffe *et al.*, "Computer-based consultations in clinical therapeutics: Explanation and rule acquisition capabilities of the MYCIN system," *Comput. Biomed. Res.*, vol. 8, pp. 303-320, 1975. https://doi.org/10.1016/0010-4809(75)90009-9  
*Supports:* Classic rule-based expert system with explanation facility.
