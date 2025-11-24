package com.fts.ttbros.documents

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.databinding.FragmentPdfViewerBinding
import com.fts.ttbros.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerFragment : Fragment() {

    private var _binding: FragmentPdfViewerBinding? = null
    private val binding get() = _binding!!
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var filePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = arguments?.getString("filePath")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (filePath != null) {
            openPdf(filePath!!)
        } else {
            binding.errorTextView.visibility = View.VISIBLE
            binding.errorTextView.text = "No file path provided"
        }
    }

    private fun openPdf(path: String) {
        if (!isAdded || view == null) {
            android.util.Log.w("PdfViewerFragment", "Fragment not attached, cannot open PDF")
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) {
                    withContext(Dispatchers.Main) {
                        if (isAdded && view != null) {
                            binding.errorTextView.visibility = View.VISIBLE
                            binding.errorTextView.text = "Файл не найден или недоступен"
                        }
                    }
                    return@launch
                }

                // Проверяем размер файла
                if (file.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        if (isAdded && view != null) {
                            binding.errorTextView.visibility = View.VISIBLE
                            binding.errorTextView.text = "Файл пуст"
                        }
                    }
                    return@launch
                }

                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)

                withContext(Dispatchers.Main) {
                    if (isAdded && view != null && pdfRenderer != null) {
                        try {
                            binding.pdfRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                            binding.pdfRecyclerView.adapter = PdfPageAdapter(pdfRenderer!!)
                            binding.errorTextView.visibility = View.GONE
                        } catch (e: Exception) {
                            android.util.Log.e("PdfViewerFragment", "Error setting up RecyclerView: ${e.message}", e)
                            binding.errorTextView.visibility = View.VISIBLE
                            binding.errorTextView.text = "Ошибка отображения PDF: ${e.message}"
                        }
                    }
                }

            } catch (e: SecurityException) {
                android.util.Log.e("PdfViewerFragment", "SecurityException opening PDF: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        binding.errorTextView.visibility = View.VISIBLE
                        binding.errorTextView.text = "Нет доступа к файлу"
                    }
                }
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("PdfViewerFragment", "OutOfMemoryError opening PDF: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        binding.errorTextView.visibility = View.VISIBLE
                        binding.errorTextView.text = "Файл слишком большой для открытия"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PdfViewerFragment", "Error opening PDF: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        binding.errorTextView.visibility = View.VISIBLE
                        binding.errorTextView.text = "Ошибка открытия PDF: ${e.message}"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _binding = null
    }

    inner class PdfPageAdapter(private val renderer: PdfRenderer) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.pageImageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.imageView.setImageBitmap(null) // Clear previous image
            
            // Render asynchronously
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                try {
                    // Check if fragment is still attached
                    if (!isAdded || view == null) return@launch
                    
                    // Check if renderer is still open
                    if (renderer.pageCount <= position) return@launch
                    
                    val page = try {
                        synchronized(renderer) {
                            renderer.openPage(position)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PdfViewerFragment", "Error opening page $position: ${e.message}", e)
                        return@launch
                    }
                    
                    val width = resources.displayMetrics.widthPixels
                    val scale = width.toFloat() / page.width
                    val height = (page.height * scale).toInt()
                    
                    val bitmap = try {
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    } catch (e: OutOfMemoryError) {
                        android.util.Log.e("PdfViewerFragment", "OutOfMemoryError creating bitmap for page $position", e)
                        page.close()
                        return@launch
                    }
                    
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } catch (e: Exception) {
                        android.util.Log.e("PdfViewerFragment", "Error rendering page $position: ${e.message}", e)
                        bitmap.recycle()
                        page.close()
                        return@launch
                    }
                    
                    page.close()
                    
                    withContext(Dispatchers.Main) {
                        if (isAdded && view != null) {
                            holder.imageView.setImageBitmap(bitmap)
                        } else {
                            bitmap.recycle()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PdfViewerFragment", "Error in onBindViewHolder for page $position: ${e.message}", e)
                }
            }
        }

        override fun getItemCount(): Int = renderer.pageCount
    }
}
